export interface Medication {
  id: string;
  name: string;
  dosage: string;
  unit: string;
  frequency: "daily" | "twice_daily" | "three_times_daily" | "weekly" | "as_needed";
  timesPerDay: number;
  scheduledTimes: string[]; // ["08:00", "20:00"]
  tabletsPerDose: number;
  currentStock: number;
  repeatsRemaining: number;
  lowStockThreshold: number; // days worth of supply
  notes: string;
  active: boolean;
  createdAt: string;
  color: string;
}

export interface DoseLog {
  id: string;
  medicationId: string;
  scheduledDate: string; // "2025-01-15"
  scheduledTime: string; // "08:00"
  status: "pending" | "taken" | "skipped" | "missed";
  takenAt: string | null;
  createdAt: string;
}

export interface StockEvent {
  id: string;
  medicationId: string;
  type: "added" | "consumed" | "adjusted";
  quantity: number;
  note: string;
  createdAt: string;
}

export type TabId = "dashboard" | "history" | "settings";

export const FREQUENCY_LABELS: Record<Medication["frequency"], string> = {
  daily: "Once daily",
  twice_daily: "Twice daily",
  three_times_daily: "Three times daily",
  weekly: "Once weekly",
  as_needed: "As needed",
};

export const FREQUENCY_TIMES: Record<Medication["frequency"], number> = {
  daily: 1,
  twice_daily: 2,
  three_times_daily: 3,
  weekly: 1,
  as_needed: 1,
};

export const DEFAULT_TIMES: Record<Medication["frequency"], string[]> = {
  daily: ["08:00"],
  twice_daily: ["08:00", "20:00"],
  three_times_daily: ["08:00", "14:00", "20:00"],
  weekly: ["08:00"],
  as_needed: ["08:00"],
};

export const MED_COLORS = [
  "#4f46e5", "#7c3aed", "#db2777", "#dc2626",
  "#ea580c", "#d97706", "#16a34a", "#0891b2",
  "#2563eb", "#4338ca", "#9333ea", "#c026d3",
];
