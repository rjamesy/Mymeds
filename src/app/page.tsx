"use client";

import { useEffect } from "react";
import { AppProvider, useApp } from "@/lib/context";
import { registerServiceWorker, scheduleNotifications } from "@/lib/notifications";
import { getNotificationsEnabled } from "@/lib/storage";
import BottomNav from "@/components/BottomNav";
import Dashboard from "@/components/Dashboard";
import History from "@/components/History";
import Settings from "@/components/Settings";
import MedicationForm from "@/components/MedicationForm";
import AddStockModal from "@/components/AddStockModal";

function AppContent() {
  const {
    activeTab,
    editingMedId,
    setEditingMedId,
    showAddMed,
    setShowAddMed,
    showAddStock,
    setShowAddStock,
    medications,
  } = useApp();

  useEffect(() => {
    registerServiceWorker();
    if (getNotificationsEnabled()) {
      scheduleNotifications();
    }
  }, []);

  // Re-schedule notifications when medications change
  useEffect(() => {
    if (getNotificationsEnabled()) {
      scheduleNotifications();
    }
  }, [medications]);

  const editingMed = editingMedId
    ? medications.find((m) => m.id === editingMedId)
    : undefined;

  return (
    <div className="min-h-[100dvh] pb-20 safe-top">
      <div className="max-w-lg mx-auto px-4 py-4">
        {activeTab === "dashboard" && <Dashboard />}
        {activeTab === "history" && <History />}
        {activeTab === "settings" && <Settings />}
      </div>

      <BottomNav />

      {/* Modals */}
      {showAddMed && (
        <MedicationForm onClose={() => setShowAddMed(false)} />
      )}
      {editingMed && (
        <MedicationForm
          medication={editingMed}
          onClose={() => setEditingMedId(null)}
        />
      )}
      {showAddStock && (
        <AddStockModal
          medId={showAddStock}
          onClose={() => setShowAddStock(null)}
        />
      )}
    </div>
  );
}

export default function Home() {
  return (
    <AppProvider>
      <AppContent />
    </AppProvider>
  );
}
