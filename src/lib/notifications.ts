"use client";

import { getMedications } from "./storage";
import { formatTime } from "./helpers";

export async function requestNotificationPermission(): Promise<boolean> {
  if (!("Notification" in window)) return false;
  if (Notification.permission === "granted") return true;
  const result = await Notification.requestPermission();
  return result === "granted";
}

export function scheduleNotifications(): void {
  if (!("serviceWorker" in navigator)) return;
  if (Notification.permission !== "granted") return;

  const meds = getMedications().filter((m) => m.active && m.frequency !== "as_needed");

  navigator.serviceWorker.ready.then((registration) => {
    const now = new Date();
    const today = now.toDateString();

    for (const med of meds) {
      for (const time of med.scheduledTimes) {
        const [h, m] = time.split(":").map(Number);
        const scheduledDate = new Date(today);
        scheduledDate.setHours(h, m, 0, 0);

        // Only schedule future notifications
        if (scheduledDate > now) {
          registration.active?.postMessage({
            type: "SCHEDULE_NOTIFICATION",
            title: `Time to take ${med.name}`,
            body: `${med.dosage} - ${med.tabletsPerDose} ${med.unit}(s) at ${formatTime(time)}`,
            scheduledTime: scheduledDate.getTime(),
          });
        }
      }
    }
  });
}

export function registerServiceWorker(): void {
  if ("serviceWorker" in navigator) {
    navigator.serviceWorker
      .register("/sw.js")
      .then(() => {
        scheduleNotifications();
      })
      .catch((err) => {
        console.error("SW registration failed:", err);
      });
  }
}
