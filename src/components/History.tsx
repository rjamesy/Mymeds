"use client";

import { useState, useMemo } from "react";
import { useApp } from "@/lib/context";
import { formatDate, formatTime, getTodayStr, getAdherenceStats } from "@/lib/helpers";
import * as storage from "@/lib/storage";
import { DoseLog } from "@/lib/types";

function CalendarGrid({
  selectedDate,
  onSelectDate,
}: {
  selectedDate: string;
  onSelectDate: (d: string) => void;
}) {
  const [monthOffset, setMonthOffset] = useState(0);

  const { year, month, days } = useMemo(() => {
    const now = new Date();
    now.setMonth(now.getMonth() + monthOffset);
    const y = now.getFullYear();
    const m = now.getMonth();
    const firstDay = new Date(y, m, 1).getDay();
    const daysInMonth = new Date(y, m + 1, 0).getDate();
    const d: (number | null)[] = [];

    // Adjust so Monday is first day of week
    const adjustedFirstDay = firstDay === 0 ? 6 : firstDay - 1;

    for (let i = 0; i < adjustedFirstDay; i++) d.push(null);
    for (let i = 1; i <= daysInMonth; i++) d.push(i);
    return { year: y, month: m, days: d };
  }, [monthOffset]);

  const monthName = new Date(year, month).toLocaleDateString("en-GB", {
    month: "long",
    year: "numeric",
  });

  const today = getTodayStr();
  const allLogs = storage.getDoseLogs();

  const getDayStatus = (day: number): "none" | "partial" | "full" | "missed" => {
    const dateStr = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    const dayLogs = allLogs.filter((l) => l.scheduledDate === dateStr);
    if (dayLogs.length === 0) return "none";
    const taken = dayLogs.filter((l) => l.status === "taken").length;
    if (taken === dayLogs.length) return "full";
    if (taken > 0) return "partial";
    const hasMissed = dayLogs.some((l) => l.status === "skipped" || l.status === "missed");
    if (hasMissed) return "missed";
    return "none";
  };

  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl p-4 shadow-sm border border-slate-200 dark:border-slate-700">
      {/* Month navigation */}
      <div className="flex items-center justify-between mb-4">
        <button
          onClick={() => setMonthOffset(monthOffset - 1)}
          className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-slate-500"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h3 className="font-semibold text-slate-800 dark:text-white">{monthName}</h3>
        <button
          onClick={() => setMonthOffset(monthOffset + 1)}
          disabled={monthOffset >= 0}
          className="w-8 h-8 rounded-lg bg-slate-100 dark:bg-slate-700 flex items-center justify-center text-slate-500 disabled:opacity-30"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>

      {/* Day headers */}
      <div className="grid grid-cols-7 mb-2">
        {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => (
          <div key={i} className="text-center text-xs font-medium text-slate-400">
            {d}
          </div>
        ))}
      </div>

      {/* Days */}
      <div className="grid grid-cols-7 gap-1">
        {days.map((day, i) => {
          if (day === null) {
            return <div key={i} />;
          }
          const dateStr = `${year}-${String(month + 1).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
          const isToday = dateStr === today;
          const isSelected = dateStr === selectedDate;
          const isFuture = dateStr > today;
          const status = getDayStatus(day);

          return (
            <button
              key={i}
              onClick={() => onSelectDate(dateStr)}
              disabled={isFuture}
              className={`aspect-square rounded-lg flex items-center justify-center text-sm font-medium transition-all relative ${
                isSelected
                  ? "bg-indigo-600 text-white"
                  : isToday
                  ? "bg-indigo-100 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300"
                  : isFuture
                  ? "text-slate-300 dark:text-slate-600"
                  : "text-slate-700 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700"
              }`}
            >
              {day}
              {!isFuture && status !== "none" && !isSelected && (
                <div
                  className={`absolute bottom-1 w-1.5 h-1.5 rounded-full ${
                    status === "full"
                      ? "bg-emerald-500"
                      : status === "partial"
                      ? "bg-amber-500"
                      : "bg-red-500"
                  }`}
                />
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}

function DayDetail({ date }: { date: string }) {
  const { medications } = useApp();
  const logs = storage.getDoseLogsForDate(date);
  const stats = getAdherenceStats(date, date);

  if (logs.length === 0) {
    return (
      <div className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-sm border border-slate-200 dark:border-slate-700 text-center">
        <p className="text-slate-400 dark:text-slate-500">No medication data for {formatDate(date)}</p>
      </div>
    );
  }

  return (
    <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-sm border border-slate-200 dark:border-slate-700">
      {/* Day header */}
      <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-700 flex items-center justify-between">
        <h3 className="font-semibold text-slate-800 dark:text-white">{formatDate(date)}</h3>
        <span
          className={`text-sm font-semibold px-2 py-0.5 rounded-full ${
            stats.adherenceRate >= 80
              ? "bg-emerald-100 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300"
              : stats.adherenceRate >= 50
              ? "bg-amber-100 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300"
              : "bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300"
          }`}
        >
          {stats.adherenceRate}%
        </span>
      </div>

      {/* Dose list */}
      <div className="divide-y divide-slate-100 dark:divide-slate-700">
        {logs.map((log) => {
          const med = medications.find((m) => m.id === log.medicationId);
          if (!med) return null;

          return (
            <div key={log.id} className="px-4 py-3 flex items-center gap-3">
              <div
                className={`w-8 h-8 rounded-lg flex items-center justify-center text-xs ${
                  log.status === "taken"
                    ? "bg-emerald-100 dark:bg-emerald-900/30 text-emerald-600"
                    : log.status === "skipped"
                    ? "bg-slate-100 dark:bg-slate-700 text-slate-400"
                    : "bg-red-100 dark:bg-red-900/30 text-red-500"
                }`}
              >
                {log.status === "taken" ? (
                  <svg className="w-4 h-4" fill="currentColor" viewBox="0 0 20 20">
                    <path fillRule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clipRule="evenodd" />
                  </svg>
                ) : log.status === "skipped" ? (
                  <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                ) : (
                  <span className="text-xs">?</span>
                )}
              </div>
              <div className="flex-1">
                <p className="text-sm font-medium text-slate-800 dark:text-white">{med.name}</p>
                <p className="text-xs text-slate-400">{med.dosage} at {formatTime(log.scheduledTime)}</p>
              </div>
              <span className={`text-xs font-medium capitalize ${
                log.status === "taken"
                  ? "text-emerald-600 dark:text-emerald-400"
                  : log.status === "skipped"
                  ? "text-slate-400"
                  : "text-red-500"
              }`}>
                {log.status}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default function History() {
  const [selectedDate, setSelectedDate] = useState(getTodayStr());

  // Overall stats
  const today = getTodayStr();
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
  const stats30 = getAdherenceStats(thirtyDaysAgo.toISOString().split("T")[0], today);

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-slate-800 dark:text-white">History</h1>

      {/* 30-day summary */}
      {stats30.total > 0 && (
        <div className="grid grid-cols-3 gap-3">
          <div className="bg-emerald-50 dark:bg-emerald-900/20 rounded-2xl p-3 text-center border border-emerald-200 dark:border-emerald-800">
            <p className="text-2xl font-bold text-emerald-700 dark:text-emerald-300">{stats30.adherenceRate}%</p>
            <p className="text-xs text-emerald-600 dark:text-emerald-400">30d Adherence</p>
          </div>
          <div className="bg-indigo-50 dark:bg-indigo-900/20 rounded-2xl p-3 text-center border border-indigo-200 dark:border-indigo-800">
            <p className="text-2xl font-bold text-indigo-700 dark:text-indigo-300">{stats30.taken}</p>
            <p className="text-xs text-indigo-600 dark:text-indigo-400">Doses Taken</p>
          </div>
          <div className="bg-slate-50 dark:bg-slate-800 rounded-2xl p-3 text-center border border-slate-200 dark:border-slate-700">
            <p className="text-2xl font-bold text-slate-700 dark:text-slate-300">{stats30.skipped}</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">Skipped</p>
          </div>
        </div>
      )}

      {/* Calendar */}
      <CalendarGrid selectedDate={selectedDate} onSelectDate={setSelectedDate} />

      {/* Day detail */}
      <DayDetail date={selectedDate} />
    </div>
  );
}
