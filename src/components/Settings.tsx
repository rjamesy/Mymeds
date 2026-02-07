"use client";

import { useApp } from "@/lib/context";
import { Medication, FREQUENCY_LABELS } from "@/lib/types";
import { getDaysSupply, getStockStatus, formatTime } from "@/lib/helpers";
import * as storage from "@/lib/storage";
import { useState, useEffect } from "react";

function MedCard({ med }: { med: Medication }) {
  const { setEditingMedId, setShowAddStock, refresh } = useApp();
  const stockStatus = getStockStatus(med);
  const daysLeft = getDaysSupply(med);

  const toggleActive = () => {
    med.active = !med.active;
    storage.upsertMedication(med);
    refresh();
  };

  return (
    <div className={`bg-white dark:bg-slate-800 rounded-2xl p-4 shadow-sm border border-slate-200 dark:border-slate-700 ${
      !med.active ? "opacity-50" : ""
    }`}>
      <div className="flex items-start gap-3">
        <div
          className="w-10 h-10 rounded-xl flex items-center justify-center text-white text-sm font-bold shrink-0"
          style={{ backgroundColor: med.color }}
        >
          {med.name.substring(0, 2).toUpperCase()}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold text-slate-800 dark:text-white truncate">{med.name}</h3>
            {!med.active && (
              <span className="text-xs bg-slate-200 dark:bg-slate-600 text-slate-500 dark:text-slate-400 px-2 py-0.5 rounded-full">
                Inactive
              </span>
            )}
          </div>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            {med.dosage} &middot; {FREQUENCY_LABELS[med.frequency]}
          </p>
          <p className="text-xs text-slate-400 dark:text-slate-500 mt-0.5">
            {med.scheduledTimes.map(formatTime).join(", ")} &middot; {med.tabletsPerDose} {med.unit}/dose
          </p>
        </div>
      </div>

      {/* Stock info */}
      <div className="mt-3 flex items-center gap-3">
        <div className={`flex-1 rounded-xl px-3 py-2 text-sm ${
          stockStatus === "empty" || stockStatus === "critical"
            ? "bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300"
            : stockStatus === "low"
            ? "bg-amber-50 dark:bg-amber-900/20 text-amber-700 dark:text-amber-300"
            : "bg-slate-50 dark:bg-slate-700/50 text-slate-600 dark:text-slate-300"
        }`}>
          <span className="font-semibold">{med.currentStock}</span> {med.unit}(s)
          {stockStatus !== "empty" && (
            <span className="text-xs ml-1">({daysLeft}d supply)</span>
          )}
          <span className="mx-1">&middot;</span>
          <span className="font-semibold">{med.repeatsRemaining}</span> repeat{med.repeatsRemaining !== 1 ? "s" : ""}
        </div>
        <button
          onClick={() => setShowAddStock(med.id)}
          className="shrink-0 w-9 h-9 rounded-xl bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400 flex items-center justify-center hover:bg-emerald-100 transition"
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
        </button>
      </div>

      {/* Actions */}
      <div className="mt-3 flex gap-2">
        <button
          onClick={() => setEditingMedId(med.id)}
          className="flex-1 py-2 text-sm font-medium text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-900/20 rounded-xl hover:bg-indigo-100 transition"
        >
          Edit
        </button>
        <button
          onClick={toggleActive}
          className="flex-1 py-2 text-sm font-medium text-slate-600 dark:text-slate-400 bg-slate-50 dark:bg-slate-700 rounded-xl hover:bg-slate-100 transition"
        >
          {med.active ? "Deactivate" : "Activate"}
        </button>
      </div>
    </div>
  );
}

function NotificationSettings() {
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    setEnabled(storage.getNotificationsEnabled());
  }, []);

  const toggleNotifications = async () => {
    if (!enabled) {
      if ("Notification" in window) {
        const permission = await Notification.requestPermission();
        if (permission === "granted") {
          storage.setNotificationsEnabled(true);
          setEnabled(true);
        }
      }
    } else {
      storage.setNotificationsEnabled(false);
      setEnabled(false);
    }
  };

  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl p-4 shadow-sm border border-slate-200 dark:border-slate-700">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-semibold text-slate-800 dark:text-white">Reminders</h3>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Get notified when it&apos;s time to take your meds
          </p>
        </div>
        <div
          className={`toggle-switch ${enabled ? "active" : ""}`}
          onClick={toggleNotifications}
        />
      </div>
    </div>
  );
}

function DataManagement() {
  const { refresh } = useApp();

  const exportData = () => {
    const data = {
      medications: storage.getMedications(),
      doseLogs: storage.getDoseLogs(),
      stockEvents: storage.getStockEvents(),
      exportedAt: new Date().toISOString(),
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `mymeds-backup-${new Date().toISOString().split("T")[0]}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const importData = () => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".json";
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = (ev) => {
        try {
          const data = JSON.parse(ev.target?.result as string);
          if (data.medications) storage.saveMedications(data.medications);
          if (data.doseLogs) storage.saveDoseLogs(data.doseLogs);
          if (data.stockEvents) storage.saveStockEvents(data.stockEvents);
          refresh();
          alert("Data imported successfully!");
        } catch {
          alert("Failed to import data. Invalid file format.");
        }
      };
      reader.readAsText(file);
    };
    input.click();
  };

  const clearAllData = () => {
    if (confirm("Are you sure you want to delete ALL data? This cannot be undone.")) {
      if (confirm("Really delete everything? Last chance!")) {
        localStorage.clear();
        refresh();
      }
    }
  };

  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl p-4 shadow-sm border border-slate-200 dark:border-slate-700 space-y-3">
      <h3 className="font-semibold text-slate-800 dark:text-white">Data</h3>
      <div className="flex gap-2">
        <button
          onClick={exportData}
          className="flex-1 py-2.5 text-sm font-medium text-slate-700 dark:text-slate-300 bg-slate-50 dark:bg-slate-700 rounded-xl hover:bg-slate-100 transition"
        >
          Export Backup
        </button>
        <button
          onClick={importData}
          className="flex-1 py-2.5 text-sm font-medium text-slate-700 dark:text-slate-300 bg-slate-50 dark:bg-slate-700 rounded-xl hover:bg-slate-100 transition"
        >
          Import Backup
        </button>
      </div>
      <button
        onClick={clearAllData}
        className="w-full py-2.5 text-sm font-medium text-red-600 dark:text-red-400 bg-red-50 dark:bg-red-900/20 rounded-xl hover:bg-red-100 transition"
      >
        Clear All Data
      </button>
    </div>
  );
}

export default function Settings() {
  const { medications, setShowAddMed } = useApp();
  const activeMeds = medications.filter((m) => m.active);
  const inactiveMeds = medications.filter((m) => !m.active);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800 dark:text-white">Settings</h1>
        <button
          onClick={() => setShowAddMed(true)}
          className="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-xl hover:bg-indigo-700 active:scale-95 transition-all shadow-lg shadow-indigo-500/25"
        >
          + Add Med
        </button>
      </div>

      {/* Notifications */}
      <NotificationSettings />

      {/* Active Medications */}
      {activeMeds.length > 0 && (
        <div>
          <h2 className="text-sm font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">
            Active Medications ({activeMeds.length})
          </h2>
          <div className="space-y-3">
            {activeMeds.map((med) => (
              <MedCard key={med.id} med={med} />
            ))}
          </div>
        </div>
      )}

      {/* Inactive Medications */}
      {inactiveMeds.length > 0 && (
        <div>
          <h2 className="text-sm font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">
            Inactive ({inactiveMeds.length})
          </h2>
          <div className="space-y-3">
            {inactiveMeds.map((med) => (
              <MedCard key={med.id} med={med} />
            ))}
          </div>
        </div>
      )}

      {/* Data Management */}
      <DataManagement />

      {/* App info */}
      <div className="text-center py-4">
        <p className="text-xs text-slate-400 dark:text-slate-500">
          MyMeds v1.0 &middot; Data stored locally on your device
        </p>
      </div>
    </div>
  );
}
