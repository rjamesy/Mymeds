"use client";

import { useApp } from "@/lib/context";
import { Medication, DoseLog } from "@/lib/types";
import {
  formatTime,
  isTimeOverdue,
  takeDose,
  skipDose,
  undoDose,
  getDaysSupply,
  getStockStatus,
  getRepeatStatus,
  getTodayStr,
  formatDate,
  getLast7DaysAdherence,
} from "@/lib/helpers";
import * as storage from "@/lib/storage";

function StockWarningBanner({ medications }: { medications: Medication[] }) {
  const warnings = medications.filter((m) => {
    const stock = getStockStatus(m);
    return stock === "critical" || stock === "empty" || stock === "low";
  });

  if (warnings.length === 0) return null;

  return (
    <div className="space-y-2 mb-4">
      {warnings.map((med) => {
        const status = getStockStatus(med);
        const repeatStatus = getRepeatStatus(med);
        const daysLeft = getDaysSupply(med);

        return (
          <div
            key={med.id}
            className={`rounded-xl p-3 flex items-center gap-3 ${
              status === "empty" || status === "critical"
                ? "bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800"
                : "bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800"
            }`}
          >
            <div
              className={`w-10 h-10 rounded-full flex items-center justify-center text-white text-lg shrink-0 ${
                status === "empty" || status === "critical" ? "bg-red-500 animate-pulse-warning" : "bg-amber-500"
              }`}
            >
              !
            </div>
            <div className="flex-1 min-w-0">
              <p className={`font-semibold text-sm ${
                status === "empty" || status === "critical" ? "text-red-800 dark:text-red-200" : "text-amber-800 dark:text-amber-200"
              }`}>
                {med.name}
              </p>
              <p className={`text-xs ${
                status === "empty" || status === "critical" ? "text-red-600 dark:text-red-300" : "text-amber-600 dark:text-amber-300"
              }`}>
                {med.currentStock === 0
                  ? "No tablets remaining!"
                  : `${med.currentStock} ${med.unit}(s) left (~${daysLeft} day${daysLeft !== 1 ? "s" : ""})`}
                {repeatStatus === "critical" && " | No repeats left!"}
                {repeatStatus === "warning" && ` | ${med.repeatsRemaining} repeat left`}
              </p>
            </div>
            <RefillButton medId={med.id} />
          </div>
        );
      })}
    </div>
  );
}

function RefillButton({ medId }: { medId: string }) {
  const { setShowAddStock } = useApp();
  return (
    <button
      onClick={() => setShowAddStock(medId)}
      className="shrink-0 px-3 py-1.5 bg-indigo-600 text-white text-xs font-medium rounded-lg hover:bg-indigo-700 active:scale-95 transition-all"
    >
      + Refill
    </button>
  );
}

function AdherenceSummary() {
  const data = getLast7DaysAdherence();
  const todayStr = getTodayStr();

  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl p-4 mb-4 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-600 dark:text-slate-300 mb-3">
        7-Day Adherence
      </h3>
      <div className="flex items-end gap-1.5 h-16">
        {data.map((day) => (
          <div key={day.date} className="flex-1 flex flex-col items-center gap-1">
            <div className="w-full relative" style={{ height: "48px" }}>
              <div
                className={`absolute bottom-0 w-full rounded-t-sm transition-all ${
                  day.date === todayStr
                    ? "bg-indigo-500"
                    : day.rate >= 80
                    ? "bg-emerald-400 dark:bg-emerald-500"
                    : day.rate >= 50
                    ? "bg-amber-400 dark:bg-amber-500"
                    : day.rate > 0
                    ? "bg-red-400 dark:bg-red-500"
                    : "bg-slate-200 dark:bg-slate-600"
                }`}
                style={{ height: `${Math.max(day.rate, 4)}%` }}
              />
            </div>
            <span className="text-[10px] text-slate-400">
              {new Date(day.date + "T00:00:00").toLocaleDateString("en-GB", { weekday: "narrow" })}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function DoseCard({ log, med }: { log: DoseLog; med: Medication }) {
  const { refresh } = useApp();

  const handleTake = () => {
    takeDose(log);
    refresh();
  };

  const handleSkip = () => {
    skipDose(log);
    refresh();
  };

  const handleUndo = () => {
    undoDose(log);
    refresh();
  };

  const overdue = log.status === "pending" && isTimeOverdue(log.scheduledTime);

  return (
    <div
      className={`rounded-2xl p-4 transition-all shadow-sm ${
        log.status === "taken"
          ? "bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800"
          : log.status === "skipped"
          ? "bg-slate-50 dark:bg-slate-800/50 border border-slate-200 dark:border-slate-700 opacity-60"
          : overdue
          ? "bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800"
          : "bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700"
      }`}
    >
      <div className="flex items-center gap-3">
        {/* Color dot */}
        <div
          className="w-11 h-11 rounded-xl flex items-center justify-center text-white text-sm font-bold shrink-0"
          style={{ backgroundColor: med.color }}
        >
          {med.name.substring(0, 2).toUpperCase()}
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="font-semibold text-slate-800 dark:text-slate-100 truncate">
              {med.name}
            </h3>
            {log.status === "taken" && (
              <span className="text-emerald-500">
                <svg className="w-5 h-5" fill="currentColor" viewBox="0 0 20 20">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clipRule="evenodd" />
                </svg>
              </span>
            )}
          </div>
          <p className="text-sm text-slate-500 dark:text-slate-400">
            {med.dosage} {med.unit} &middot; {formatTime(log.scheduledTime)}
            {overdue && <span className="text-red-500 font-medium"> &middot; Overdue</span>}
          </p>
          <p className="text-xs text-slate-400 dark:text-slate-500 mt-0.5">
            {med.currentStock} {med.unit}(s) remaining
          </p>
        </div>

        {/* Actions */}
        <div className="flex gap-2 shrink-0">
          {log.status === "pending" && (
            <>
              <button
                onClick={handleTake}
                className="w-10 h-10 rounded-xl bg-emerald-500 text-white flex items-center justify-center hover:bg-emerald-600 active:scale-90 transition-all shadow-sm"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
                </svg>
              </button>
              <button
                onClick={handleSkip}
                className="w-10 h-10 rounded-xl bg-slate-200 dark:bg-slate-600 text-slate-500 dark:text-slate-300 flex items-center justify-center hover:bg-slate-300 active:scale-90 transition-all"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </>
          )}
          {(log.status === "taken" || log.status === "skipped") && (
            <button
              onClick={handleUndo}
              className="px-3 py-1.5 text-xs text-slate-500 dark:text-slate-400 hover:text-slate-700 transition-colors"
            >
              Undo
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default function Dashboard() {
  const { medications, todaysDoses } = useApp();
  const activeMeds = medications.filter((m) => m.active);
  const today = getTodayStr();

  const taken = todaysDoses.filter((d) => d.status === "taken").length;
  const total = todaysDoses.length;
  const progress = total > 0 ? Math.round((taken / total) * 100) : 0;

  if (activeMeds.length === 0) {
    return (
      <EmptyState />
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">Today</h1>
          <p className="text-sm text-slate-500 dark:text-slate-400">{formatDate(today)}</p>
        </div>
        <div className="relative w-14 h-14">
          <svg className="w-14 h-14 -rotate-90" viewBox="0 0 56 56">
            <circle cx="28" cy="28" r="24" fill="none" stroke="#e2e8f0" strokeWidth="4" className="dark:stroke-slate-700" />
            <circle
              cx="28" cy="28" r="24" fill="none"
              stroke={progress === 100 ? "#22c55e" : "#4f46e5"}
              strokeWidth="4"
              strokeLinecap="round"
              strokeDasharray={`${progress * 1.508} 150.8`}
              className="transition-all duration-500"
            />
          </svg>
          <div className="absolute inset-0 flex items-center justify-center">
            <span className="text-xs font-bold text-slate-700 dark:text-slate-200">
              {taken}/{total}
            </span>
          </div>
        </div>
      </div>

      {/* Stock Warnings */}
      <StockWarningBanner medications={activeMeds} />

      {/* Adherence Chart */}
      <AdherenceSummary />

      {/* Today's Schedule */}
      <div>
        <h2 className="text-sm font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-3">
          Schedule
        </h2>
        <div className="space-y-3">
          {todaysDoses.map((log) => {
            const med = medications.find((m) => m.id === log.medicationId);
            if (!med) return null;
            return <DoseCard key={log.id} log={log} med={med} />;
          })}
        </div>
      </div>
    </div>
  );
}

function EmptyState() {
  const { setActiveTab, setShowAddMed } = useApp();

  return (
    <div className="flex flex-col items-center justify-center py-20 text-center">
      <div className="w-20 h-20 rounded-full bg-indigo-100 dark:bg-indigo-900/30 flex items-center justify-center mb-6">
        <svg className="w-10 h-10 text-indigo-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m3.75 9v6m3-3H9m1.5-12H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
        </svg>
      </div>
      <h2 className="text-xl font-bold text-slate-800 dark:text-white mb-2">No medications yet</h2>
      <p className="text-slate-500 dark:text-slate-400 mb-6 max-w-xs">
        Add your medications to start tracking doses and managing your prescriptions.
      </p>
      <button
        onClick={() => {
          setActiveTab("settings");
          setTimeout(() => setShowAddMed(true), 100);
        }}
        className="px-6 py-3 bg-indigo-600 text-white rounded-xl font-medium hover:bg-indigo-700 active:scale-95 transition-all shadow-lg shadow-indigo-500/25"
      >
        + Add First Medication
      </button>
    </div>
  );
}
