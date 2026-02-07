const CACHE_NAME = "mymeds-v1";

// Install
self.addEventListener("install", (event) => {
  self.skipWaiting();
});

// Activate
self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(
        keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key))
      )
    )
  );
  self.clients.claim();
});

// Fetch - network first, cache fallback
self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") return;

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        const clone = response.clone();
        caches.open(CACHE_NAME).then((cache) => {
          cache.put(event.request, clone);
        });
        return response;
      })
      .catch(() => caches.match(event.request))
  );
});

// Notification scheduling
self.addEventListener("message", (event) => {
  if (event.data.type === "SCHEDULE_NOTIFICATION") {
    const { title, body, scheduledTime } = event.data;
    const now = Date.now();
    const delay = scheduledTime - now;

    if (delay > 0) {
      setTimeout(() => {
        self.registration.showNotification(title, {
          body,
          icon: "/icon-192.png",
          badge: "/icon-192.png",
          vibrate: [200, 100, 200],
          tag: `mymeds-${scheduledTime}`,
          renotify: true,
          actions: [
            { action: "taken", title: "Taken" },
            { action: "snooze", title: "Snooze 10m" },
          ],
        });
      }, delay);
    }
  }
});

// Handle notification actions
self.addEventListener("notificationclick", (event) => {
  event.notification.close();

  if (event.action === "snooze") {
    setTimeout(() => {
      self.registration.showNotification("Medication Reminder", {
        body: "Time to take your medication!",
        icon: "/icon-192.png",
        vibrate: [200, 100, 200],
      });
    }, 10 * 60 * 1000);
  }

  event.waitUntil(
    self.clients.matchAll({ type: "window" }).then((clients) => {
      if (clients.length > 0) {
        clients[0].focus();
      } else {
        self.clients.openWindow("/");
      }
    })
  );
});
