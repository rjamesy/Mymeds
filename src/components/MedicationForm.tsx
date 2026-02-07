"use client";

import { useState, useEffect } from "react";
import { Medication, FREQUENCY_LABELS, DEFAULT_TIMES, MED_COLORS } from "@/lib/types";
import { useApp } from "@/lib/context";
import { generateId } from "@/lib/helpers";
import * as storage from "@/lib/storage";

interface Props {
  medication?: Medication;
  onClose: () => void;
}

export default function MedicationForm({ medication, onClose }: Props) {
  const { refresh } = useApp();
  const isEditing = !!medication;

  const [name, setName] = useState(medication?.name || "");
  const [dosage, setDosage] = useState(medication?.dosage || "");
  const [unit, setUnit] = useState(medication?.unit || "tablet");
  const [frequency, setFrequency] = useState<Medication["frequency"]>(
    medication?.frequency || "daily"
  );
  const [scheduledTimes, setScheduledTimes] = useState<string[]>(
    medication?.scheduledTimes || ["08:00"]
  );
  const [tabletsPerDose, setTabletsPerDose] = useState(
    medication?.tabletsPerDose?.toString() || "1"
  );
  const [currentStock, setCurrentStock] = useState(
    medication?.currentStock?.toString() || "30"
  );
  const [repeatsRemaining, setRepeatsRemaining] = useState(
    medication?.repeatsRemaining?.toString() || "3"
  );
  const [lowStockThreshold, setLowStockThreshold] = useState(
    medication?.lowStockThreshold?.toString() || "7"
  );
  const [notes, setNotes] = useState(medication?.notes || "");
  const [color, setColor] = useState(
    medication?.color || MED_COLORS[Math.floor(Math.random() * MED_COLORS.length)]
  );

  useEffect(() => {
    if (!isEditing) {
      setScheduledTimes(DEFAULT_TIMES[frequency]);
    }
  }, [frequency, isEditing]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const timesPerDay = scheduledTimes.length;

    const med: Medication = {
      id: medication?.id || generateId(),
      name: name.trim(),
      dosage: dosage.trim(),
      unit,
      frequency,
      timesPerDay,
      scheduledTimes,
      tabletsPerDose: parseInt(tabletsPerDose) || 1,
      currentStock: parseInt(currentStock) || 0,
      repeatsRemaining: parseInt(repeatsRemaining) || 0,
      lowStockThreshold: parseInt(lowStockThreshold) || 7,
      notes,
      active: medication?.active ?? true,
      createdAt: medication?.createdAt || new Date().toISOString(),
      color,
    };

    storage.upsertMedication(med);
    refresh();
    onClose();
  };

  const handleDelete = () => {
    if (medication && confirm(`Delete ${medication.name}? This will also remove all dose history.`)) {
      storage.deleteMedication(medication.id);
      refresh();
      onClose();
    }
  };

  const updateTime = (idx: number, value: string) => {
    const updated = [...scheduledTimes];
    updated[idx] = value;
    setScheduledTimes(updated);
  };

  const addTime = () => {
    setScheduledTimes([...scheduledTimes, "12:00"]);
  };

  const removeTime = (idx: number) => {
    if (scheduledTimes.length <= 1) return;
    setScheduledTimes(scheduledTimes.filter((_, i) => i !== idx));
  };

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center justify-center">
      <div className="bg-white dark:bg-slate-800 w-full max-w-lg max-h-[90dvh] overflow-y-auto rounded-t-3xl sm:rounded-3xl">
        {/* Header */}
        <div className="sticky top-0 bg-white dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700 px-6 py-4 flex items-center justify-between z-10">
          <h2 className="text-lg font-bold text-slate-800 dark:text-white">
            {isEditing ? "Edit Medication" : "Add Medication"}
          </h2>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-full bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-slate-500"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          {/* Name */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Medication Name *
            </label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Metformin"
              required
              className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
            />
          </div>

          {/* Dosage + Unit */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                Dosage
              </label>
              <input
                type="text"
                value={dosage}
                onChange={(e) => setDosage(e.target.value)}
                placeholder="e.g. 500mg"
                className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                Unit
              </label>
              <select
                value={unit}
                onChange={(e) => setUnit(e.target.value)}
                className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
              >
                <option value="tablet">Tablet</option>
                <option value="capsule">Capsule</option>
                <option value="ml">ml</option>
                <option value="drop">Drop</option>
                <option value="puff">Puff</option>
                <option value="patch">Patch</option>
                <option value="injection">Injection</option>
              </select>
            </div>
          </div>

          {/* Frequency */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Frequency
            </label>
            <select
              value={frequency}
              onChange={(e) => setFrequency(e.target.value as Medication["frequency"])}
              className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
            >
              {Object.entries(FREQUENCY_LABELS).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </div>

          {/* Scheduled Times — hidden for as-needed medications */}
          {frequency !== "as_needed" && (
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                Scheduled Times
              </label>
              <div className="space-y-2">
                {scheduledTimes.map((time, idx) => (
                  <div key={idx} className="flex items-center gap-2">
                    <input
                      type="time"
                      value={time}
                      onChange={(e) => updateTime(idx, e.target.value)}
                      className="flex-1 px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
                    />
                    {scheduledTimes.length > 1 && (
                      <button
                        type="button"
                        onClick={() => removeTime(idx)}
                        className="w-10 h-10 rounded-xl bg-red-50 dark:bg-red-900/30 text-red-500 flex items-center justify-center"
                      >
                        <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                          <path strokeLinecap="round" strokeLinejoin="round" d="M20 12H4" />
                        </svg>
                      </button>
                    )}
                  </div>
                ))}
                <button
                  type="button"
                  onClick={addTime}
                  className="text-sm text-indigo-600 dark:text-indigo-400 font-medium hover:text-indigo-700 transition"
                >
                  + Add another time
                </button>
              </div>
            </div>
          )}

          {/* Tablets per dose */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Tablets per Dose
            </label>
            <input
              type="number"
              min="1"
              max="20"
              value={tabletsPerDose}
              onChange={(e) => setTabletsPerDose(e.target.value)}
              className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
            />
          </div>

          {/* Stock */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                Current Stock
              </label>
              <input
                type="number"
                min="0"
                value={currentStock}
                onChange={(e) => setCurrentStock(e.target.value)}
                className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
                Repeats Left
              </label>
              <input
                type="number"
                min="0"
                value={repeatsRemaining}
                onChange={(e) => setRepeatsRemaining(e.target.value)}
                className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
              />
            </div>
          </div>

          {/* Low stock threshold */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Low Stock Warning (days supply)
            </label>
            <input
              type="number"
              min="1"
              max="90"
              value={lowStockThreshold}
              onChange={(e) => setLowStockThreshold(e.target.value)}
              className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
            />
          </div>

          {/* Color */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Color
            </label>
            <div className="flex gap-2 flex-wrap">
              {MED_COLORS.map((c) => (
                <button
                  key={c}
                  type="button"
                  onClick={() => setColor(c)}
                  className={`w-8 h-8 rounded-full transition-all ${
                    color === c ? "ring-2 ring-offset-2 ring-indigo-500 scale-110" : "hover:scale-105"
                  }`}
                  style={{ backgroundColor: c }}
                />
              ))}
            </div>
          </div>

          {/* Notes */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Notes
            </label>
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={2}
              placeholder="e.g. Take with food"
              className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition resize-none"
            />
          </div>

          {/* Buttons */}
          <div className="flex gap-3 pt-2">
            {isEditing && (
              <button
                type="button"
                onClick={handleDelete}
                className="px-4 py-3 bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded-xl font-medium hover:bg-red-100 transition"
              >
                Delete
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-3 bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-xl font-medium hover:bg-slate-200 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 px-4 py-3 bg-indigo-600 text-white rounded-xl font-medium hover:bg-indigo-700 active:scale-[0.98] transition-all shadow-lg shadow-indigo-500/25"
            >
              {isEditing ? "Save Changes" : "Add Medication"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
