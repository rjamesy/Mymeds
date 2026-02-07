import { v4 as uuidv4 } from "uuid";
import { Medication, DoseLog, StockEvent } from "./types";
import * as storage from "./storage";

export function generateId(): string {
  return uuidv4();
}

export function getTodayStr(): string {
  return new Date().toISOString().split("T")[0];
}

export function formatDate(dateStr: string): string {
  const d = new Date(dateStr + "T00:00:00");
  return d.toLocaleDateString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
}

export function formatTime(time24: string): string {
  const [h, m] = time24.split(":").map(Number);
  const ampm = h >= 12 ? "PM" : "AM";
  const hour = h % 12 || 12;
  return `${hour}:${m.toString().padStart(2, "0")} ${ampm}`;
}

export function isTimeOverdue(scheduledTime: string): boolean {
  const now = new Date();
  const [h, m] = scheduledTime.split(":").map(Number);
  const scheduled = new Date();
  scheduled.setHours(h, m, 0, 0);
  return now > scheduled;
}

export function getDaysSupply(med: Medication): number {
  const dailyConsumption = med.timesPerDay * med.tabletsPerDose;
  if (dailyConsumption === 0) return Infinity;
  return Math.floor(med.currentStock / dailyConsumption);
}

export function getStockStatus(med: Medication): "ok" | "low" | "critical" | "empty" {
  if (med.currentStock <= 0) return "empty";
  const daysLeft = getDaysSupply(med);
  if (daysLeft <= 1) return "critical";
  if (daysLeft <= med.lowStockThreshold) return "low";
  return "ok";
}

export function getRepeatStatus(med: Medication): "ok" | "warning" | "critical" {
  if (med.repeatsRemaining <= 0) return "critical";
  if (med.repeatsRemaining <= 1) return "warning";
  return "ok";
}

// Generate today's dose schedule for all active medications
export function generateTodaysDoses(): DoseLog[] {
  const today = getTodayStr();
  const meds = storage.getMedications().filter((m) => m.active);
  const existingLogs = storage.getDoseLogsForDate(today);

  const doses: DoseLog[] = [];

  for (const med of meds) {
    if (med.frequency === "as_needed") continue;

    // Skip weekly meds if not the right day
    if (med.frequency === "weekly") {
      const createdDay = new Date(med.createdAt).getDay();
      const todayDay = new Date().getDay();
      if (createdDay !== todayDay) continue;
    }

    for (const time of med.scheduledTimes) {
      const existing = existingLogs.find(
        (l) => l.medicationId === med.id && l.scheduledTime === time
      );

      if (existing) {
        doses.push(existing);
      } else {
        const newLog: DoseLog = {
          id: generateId(),
          medicationId: med.id,
          scheduledDate: today,
          scheduledTime: time,
          status: "pending",
          takenAt: null,
          createdAt: new Date().toISOString(),
        };
        storage.upsertDoseLog(newLog);
        doses.push(newLog);
      }
    }
  }

  return doses.sort((a, b) => a.scheduledTime.localeCompare(b.scheduledTime));
}

export function takeDose(log: DoseLog): void {
  const med = storage.getMedication(log.medicationId);
  if (!med) return;

  // Update the log
  log.status = "taken";
  log.takenAt = new Date().toISOString();
  storage.upsertDoseLog(log);

  // Decrement stock
  med.currentStock = Math.max(0, med.currentStock - med.tabletsPerDose);
  storage.upsertMedication(med);

  // Log stock event
  const event: StockEvent = {
    id: generateId(),
    medicationId: med.id,
    type: "consumed",
    quantity: med.tabletsPerDose,
    note: `Took ${med.tabletsPerDose} ${med.unit} at ${formatTime(log.scheduledTime)}`,
    createdAt: new Date().toISOString(),
  };
  storage.addStockEvent(event);
}

export function skipDose(log: DoseLog): void {
  log.status = "skipped";
  storage.upsertDoseLog(log);
}

export function undoDose(log: DoseLog): void {
  const med = storage.getMedication(log.medicationId);
  if (!med) return;

  if (log.status === "taken") {
    // Restore stock
    med.currentStock += med.tabletsPerDose;
    storage.upsertMedication(med);
  }

  log.status = "pending";
  log.takenAt = null;
  storage.upsertDoseLog(log);
}

export function addStock(medId: string, quantity: number, note: string = ""): void {
  const med = storage.getMedication(medId);
  if (!med) return;

  med.currentStock += quantity;
  storage.upsertMedication(med);

  const event: StockEvent = {
    id: generateId(),
    medicationId: med.id,
    type: "added",
    quantity,
    note: note || `Added ${quantity} ${med.unit}`,
    createdAt: new Date().toISOString(),
  };
  storage.addStockEvent(event);
}

export function useRepeat(medId: string): void {
  const med = storage.getMedication(medId);
  if (!med) return;

  if (med.repeatsRemaining > 0) {
    med.repeatsRemaining -= 1;
    storage.upsertMedication(med);
  }
}

// Get adherence stats for a date range
export function getAdherenceStats(startDate: string, endDate: string) {
  const logs = storage.getDoseLogs().filter(
    (l) => l.scheduledDate >= startDate && l.scheduledDate <= endDate
  );

  const total = logs.length;
  const taken = logs.filter((l) => l.status === "taken").length;
  const skipped = logs.filter((l) => l.status === "skipped").length;
  const missed = logs.filter((l) => l.status === "missed").length;
  const pending = logs.filter((l) => l.status === "pending").length;

  return {
    total,
    taken,
    skipped,
    missed,
    pending,
    adherenceRate: total > 0 ? Math.round((taken / (total - pending)) * 100) || 0 : 0,
  };
}

// Get past 7 days for chart
export function getLast7DaysAdherence(): { date: string; rate: number }[] {
  const days: { date: string; rate: number }[] = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const dateStr = d.toISOString().split("T")[0];
    const stats = getAdherenceStats(dateStr, dateStr);
    days.push({ date: dateStr, rate: stats.adherenceRate });
  }
  return days;
}
