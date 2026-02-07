import { Medication, DoseLog, StockEvent } from "./types";

const KEYS = {
  medications: "mymeds_medications",
  doseLogs: "mymeds_dose_logs",
  stockEvents: "mymeds_stock_events",
  notificationsEnabled: "mymeds_notifications_enabled",
};

function getItem<T>(key: string, fallback: T): T {
  if (typeof window === "undefined") return fallback;
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function setItem<T>(key: string, value: T): void {
  if (typeof window === "undefined") return;
  localStorage.setItem(key, JSON.stringify(value));
}

// Medications
export function getMedications(): Medication[] {
  return getItem<Medication[]>(KEYS.medications, []);
}

export function saveMedications(meds: Medication[]): void {
  setItem(KEYS.medications, meds);
}

export function getMedication(id: string): Medication | undefined {
  return getMedications().find((m) => m.id === id);
}

export function upsertMedication(med: Medication): void {
  const meds = getMedications();
  const idx = meds.findIndex((m) => m.id === med.id);
  if (idx >= 0) {
    meds[idx] = med;
  } else {
    meds.push(med);
  }
  saveMedications(meds);
}

export function deleteMedication(id: string): void {
  saveMedications(getMedications().filter((m) => m.id !== id));
  // Also clean up dose logs
  saveDoseLogs(getDoseLogs().filter((d) => d.medicationId !== id));
}

// Dose Logs
export function getDoseLogs(): DoseLog[] {
  return getItem<DoseLog[]>(KEYS.doseLogs, []);
}

export function saveDoseLogs(logs: DoseLog[]): void {
  setItem(KEYS.doseLogs, logs);
}

export function getDoseLogsForDate(date: string): DoseLog[] {
  return getDoseLogs().filter((d) => d.scheduledDate === date);
}

export function getDoseLogsForMedication(medId: string): DoseLog[] {
  return getDoseLogs().filter((d) => d.medicationId === medId);
}

export function upsertDoseLog(log: DoseLog): void {
  const logs = getDoseLogs();
  const idx = logs.findIndex((l) => l.id === log.id);
  if (idx >= 0) {
    logs[idx] = log;
  } else {
    logs.push(log);
  }
  saveDoseLogs(logs);
}

// Stock Events
export function getStockEvents(): StockEvent[] {
  return getItem<StockEvent[]>(KEYS.stockEvents, []);
}

export function saveStockEvents(events: StockEvent[]): void {
  setItem(KEYS.stockEvents, events);
}

export function addStockEvent(event: StockEvent): void {
  const events = getStockEvents();
  events.push(event);
  saveStockEvents(events);
}

// Notifications preference
export function getNotificationsEnabled(): boolean {
  return getItem<boolean>(KEYS.notificationsEnabled, false);
}

export function setNotificationsEnabled(enabled: boolean): void {
  setItem(KEYS.notificationsEnabled, enabled);
}
