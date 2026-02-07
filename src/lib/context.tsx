"use client";

import React, { createContext, useContext, useState, useEffect, useCallback, ReactNode } from "react";
import { Medication, DoseLog, TabId } from "./types";
import * as storage from "./storage";
import { generateTodaysDoses, getTodayStr } from "./helpers";

interface AppState {
  medications: Medication[];
  todaysDoses: DoseLog[];
  activeTab: TabId;
  setActiveTab: (tab: TabId) => void;
  refresh: () => void;
  editingMedId: string | null;
  setEditingMedId: (id: string | null) => void;
  showAddMed: boolean;
  setShowAddMed: (show: boolean) => void;
  showAddStock: string | null;
  setShowAddStock: (medId: string | null) => void;
}

const AppContext = createContext<AppState | null>(null);

export function AppProvider({ children }: { children: ReactNode }) {
  const [medications, setMedications] = useState<Medication[]>([]);
  const [todaysDoses, setTodaysDoses] = useState<DoseLog[]>([]);
  const [activeTab, setActiveTab] = useState<TabId>("dashboard");
  const [editingMedId, setEditingMedId] = useState<string | null>(null);
  const [showAddMed, setShowAddMed] = useState(false);
  const [showAddStock, setShowAddStock] = useState<string | null>(null);

  const lastKnownDate = React.useRef(getTodayStr());

  const refresh = useCallback(() => {
    lastKnownDate.current = getTodayStr();
    setMedications(storage.getMedications());
    setTodaysDoses(generateTodaysDoses());
  }, []);

  useEffect(() => {
    refresh();

    // Check every minute for day change and overdue doses
    const interval = setInterval(() => {
      const today = getTodayStr();

      // Refresh if the day has changed
      if (today !== lastKnownDate.current) {
        refresh();
        return;
      }

      const logs = storage.getDoseLogsForDate(today);
      const now = new Date();
      let changed = false;

      for (const log of logs) {
        // Skip PRN/as-needed doses — they can't be overdue
        if (log.status === "pending" && log.scheduledTime !== "PRN") {
          const [h, m] = log.scheduledTime.split(":").map(Number);
          const scheduled = new Date();
          scheduled.setHours(h, m + 30, 0, 0); // 30 min grace period
          if (now > scheduled) {
            // Still show as pending but could mark as missed
            // For now we leave as pending so user can still take it
          }
        }
      }

      if (changed) refresh();
    }, 60000);

    return () => clearInterval(interval);
  }, [refresh]);

  // Refresh when the app comes back to the foreground (e.g. next day)
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (!document.hidden) {
        const today = getTodayStr();
        if (today !== lastKnownDate.current) {
          refresh();
        }
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [refresh]);

  return (
    <AppContext.Provider
      value={{
        medications,
        todaysDoses,
        activeTab,
        setActiveTab,
        refresh,
        editingMedId,
        setEditingMedId,
        showAddMed,
        setShowAddMed,
        showAddStock,
        setShowAddStock,
      }}
    >
      {children}
    </AppContext.Provider>
  );
}

export function useApp(): AppState {
  const ctx = useContext(AppContext);
  if (!ctx) throw new Error("useApp must be used within AppProvider");
  return ctx;
}
