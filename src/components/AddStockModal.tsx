"use client";

import { useState } from "react";
import { useApp } from "@/lib/context";
import { addStock, useRepeat } from "@/lib/helpers";
import * as storage from "@/lib/storage";

interface Props {
  medId: string;
  onClose: () => void;
}

export default function AddStockModal({ medId, onClose }: Props) {
  const { refresh } = useApp();
  const med = storage.getMedication(medId);
  const [quantity, setQuantity] = useState("28");
  const [useRepeatCheck, setUseRepeatCheck] = useState(true);
  const [note, setNote] = useState("");

  if (!med) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const qty = parseInt(quantity);
    if (!qty || qty <= 0) return;

    addStock(medId, qty, note);
    if (useRepeatCheck && med.repeatsRemaining > 0) {
      useRepeat(medId);
    }
    refresh();
    onClose();
  };

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-end sm:items-center justify-center">
      <div className="bg-white dark:bg-slate-800 w-full max-w-md rounded-t-3xl sm:rounded-3xl">
        {/* Header */}
        <div className="border-b border-slate-200 dark:border-slate-700 px-6 py-4 flex items-center justify-between">
          <h2 className="text-lg font-bold text-slate-800 dark:text-white">
            Refill {med.name}
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

        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Current stock info */}
          <div className="bg-slate-50 dark:bg-slate-700/50 rounded-xl p-3">
            <div className="flex justify-between text-sm">
              <span className="text-slate-500 dark:text-slate-400">Current stock</span>
              <span className="font-semibold text-slate-800 dark:text-white">
                {med.currentStock} {med.unit}(s)
              </span>
            </div>
            <div className="flex justify-between text-sm mt-1">
              <span className="text-slate-500 dark:text-slate-400">Repeats remaining</span>
              <span className={`font-semibold ${
                med.repeatsRemaining <= 1 ? "text-red-500" : "text-slate-800 dark:text-white"
              }`}>
                {med.repeatsRemaining}
              </span>
            </div>
          </div>

          {/* Quantity */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              How many {med.unit}s are you adding?
            </label>
            <input
              type="number"
              min="1"
              value={quantity}
              onChange={(e) => setQuantity(e.target.value)}
              className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white text-lg font-semibold focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
              autoFocus
            />
          </div>

          {/* Quick select buttons */}
          <div className="flex gap-2">
            {[14, 28, 30, 56, 60, 90].map((n) => (
              <button
                key={n}
                type="button"
                onClick={() => setQuantity(n.toString())}
                className={`flex-1 py-2 rounded-lg text-sm font-medium transition ${
                  quantity === n.toString()
                    ? "bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 ring-1 ring-indigo-300"
                    : "bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200"
                }`}
              >
                {n}
              </button>
            ))}
          </div>

          {/* Use a repeat */}
          {med.repeatsRemaining > 0 && (
            <label className="flex items-center gap-3 cursor-pointer">
              <div
                className={`toggle-switch ${useRepeatCheck ? "active" : ""}`}
                onClick={() => setUseRepeatCheck(!useRepeatCheck)}
              />
              <span className="text-sm text-slate-700 dark:text-slate-300">
                Use 1 prescription repeat ({med.repeatsRemaining} left)
              </span>
            </label>
          )}

          {/* Note */}
          <div>
            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">
              Note (optional)
            </label>
            <input
              type="text"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="e.g. Picked up from pharmacy"
              className="w-full px-4 py-3 rounded-xl border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-700 text-slate-800 dark:text-white placeholder-slate-400 focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition"
            />
          </div>

          {/* Submit */}
          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-3 bg-slate-100 dark:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-xl font-medium hover:bg-slate-200 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 px-4 py-3 bg-emerald-600 text-white rounded-xl font-medium hover:bg-emerald-700 active:scale-[0.98] transition-all shadow-lg shadow-emerald-500/25"
            >
              Add Stock
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
