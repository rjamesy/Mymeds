# MyMeds

A medication management and adherence tracking app for Android, built with Jetpack Compose and Material 3.

MyMeds helps people stay on top of their medication schedules with smart reminders, dose tracking, stock management, and drug interaction warnings — all in a clean, offline-first experience.

---

## Problem

Managing multiple medications is error-prone. People forget doses, lose track of refills, and unknowingly take drugs that interact. Existing solutions are often cluttered, require accounts, or push subscriptions.

## Who It's For

- Individuals managing daily medications
- Caregivers tracking medications for others
- Anyone who wants reliable, private medication reminders without cloud accounts

## Status

**Active development** — v1.8 (versionCode 11). Functional and stable for personal use. Not yet published to Google Play.

**Production readiness:** Beta. Core features are complete and tested on physical devices. No backend or analytics — fully offline.

---

## Key Features

### Medication Management
- Add, edit, and delete medications with dosage, unit, and notes
- 7 frequency options: daily, twice daily, 3x daily, every other day, every X hours, weekly, as needed (PRN)
- Custom scheduled times per medication
- Configurable dose interval (1–6 hours) and tablets per dose
- Per-medication color coding (16 colours)
- Active/inactive toggle for pausing medications

### Dose Tracking & Reminders
- Automatic daily dose log generation
- Dose states: pending, taken, skipped, missed
- Exact alarm scheduling via `AlarmManager` (Android 12+)
- 15-minute snooze action directly from notifications
- Overdue detection with `WorkManager` fallback (checks every 15 minutes)
- Automatic dose rescheduling when a dose is taken late
- Alarms survive device reboots via `BootReceiver`

### Drug Interaction Checking
- ~220 documented drug interactions in a local database
- Automatic pairwise checking across all active medications
- Severity levels: high, moderate, low
- Covers blood thinners, NSAIDs, statins, SSRIs, benzodiazepines, opioids, antibiotics, and more

### Adherence Tracking
- Daily adherence percentage
- 30-day summary statistics (total, taken, skipped, missed, pending)
- 7-day rolling adherence chart
- Interactive calendar view with per-day breakdowns

### Stock Management
- Track current stock and low-stock thresholds per medication
- Days-of-supply calculations based on frequency
- Stock status indicators: OK, low, critical, empty
- Daily low-stock alert notifications
- Stock event history (consumed, added, adjusted)

### Data Management
- Full JSON export (backup)
- JSON import (restore)
- Clear all data with two-step confirmation

### UI Customisation
- Material 3 with dynamic theming
- Theme modes: system default, light, dark
- Pull-to-refresh on dashboard
- Sort-by-status toggle

---

## Screenshots

*Coming soon*

---

## Architecture

```
┌──────────────────────────────────────────────┐
│                    UI Layer                   │
│  ┌────────────┐ ┌─────────┐ ┌──────────────┐│
│  │ Dashboard   │ │ History │ │   Settings   ││
│  │   Screen    │ │  Screen │ │    Screen    ││
│  └──────┬─────┘ └────┬────┘ └──────┬───────┘│
│         └────────────┬┘─────────────┘        │
│              ┌───────▼────────┐              │
│              │  MedsViewModel │              │
│              └───────┬────────┘              │
├──────────────────────┼───────────────────────┤
│                Data Layer                    │
│              ┌───────▼────────┐              │
│              │ MedsRepository │              │
│              └───────┬────────┘              │
│         ┌────────────┼────────────┐          │
│    ┌────▼────┐ ┌─────▼─────┐ ┌───▼──────┐   │
│    │  Room   │ │  SharedPr │ │  Drug    │   │
│    │Database │ │    efs    │ │Interact. │   │
│    └─────────┘ └───────────┘ └──────────┘   │
├──────────────────────────────────────────────┤
│            Notification Layer                │
│  ┌──────────────┐ ┌────────────────────────┐ │
│  │AlarmScheduler│ │  OverdueDoseWorker     │ │
│  │  + Receiver  │ │  (WorkManager fallback)│ │
│  └──────────────┘ └────────────────────────┘ │
│  ┌──────────────┐ ┌────────────────────────┐ │
│  │BootReceiver  │ │   SnoozeReceiver       │ │
│  └──────────────┘ └────────────────────────┘ │
└──────────────────────────────────────────────┘
```

**Pattern:** MVVM with Repository — ViewModel exposes `StateFlow` to Compose UI, Repository wraps Room DAOs and business logic.

**Concurrency:** Coroutines with `Dispatchers.IO` and `SupervisorJob`. Race condition prevention via dose log deduplication during reconciliation.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose (BOM 2024.12.01), Material 3 |
| Database | Room 2.6.1 (SQLite) |
| Architecture | MVVM, Repository, StateFlow |
| Background | WorkManager 2.10.0, AlarmManager |
| Navigation | Navigation Compose 2.8.5 |
| Lifecycle | Lifecycle 2.8.7 (ViewModel, LiveData) |
| Build | Gradle (Kotlin DSL), KSP 2.0.21-1.0.28 |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 35 (Android 15) |
| Java | 17 |

---

## Dependencies

All versions managed in [`gradle/libs.versions.toml`](gradle/libs.versions.toml):

- **Compose BOM 2024.12.01** — UI toolkit, Material 3, extended icons
- **Room 2.6.1** — database ORM with KSP annotation processing
- **Navigation Compose 2.8.5** — screen routing
- **WorkManager 2.10.0** — periodic background checks
- **Lifecycle 2.8.7** — ViewModel, runtime, LiveData
- **Activity Compose 1.9.3** — Compose activity integration
- **Core KTX 1.15.0** — Kotlin extensions

No network dependencies. Fully offline.

---

## Repository Structure

```
Mymeds/
├── app/
│   ├── build.gradle.kts              # App-level build config (v1.8)
│   └── src/main/
│       ├── AndroidManifest.xml        # Permissions & receivers
│       └── java/com/mymeds/app/
│           ├── MainActivity.kt        # Single-activity entry point
│           ├── MyMedsApp.kt           # Application class
│           ├── data/
│           │   ├── db/
│           │   │   ├── AppDatabase.kt # Room database (v2)
│           │   │   └── Daos.kt        # Data access objects
│           │   ├── model/
│           │   │   ├── Entities.kt    # Medication, DoseLog, StockEvent
│           │   │   └── DrugInteractions.kt  # ~220 interactions
│           │   └── repository/
│           │       └── MedsRepository.kt    # Business logic
│           ├── ui/
│           │   ├── screens/
│           │   │   ├── DashboardScreen.kt   # Main dose tracking
│           │   │   ├── HistoryScreen.kt     # Calendar & adherence
│           │   │   └── SettingsScreen.kt    # Preferences & data
│           │   ├── components/
│           │   │   ├── MedicationFormDialog.kt  # Add/edit form
│           │   │   └── AddStockDialog.kt        # Stock adjustment
│           │   ├── navigation/
│           │   │   └── AppNavigation.kt     # 3-tab bottom nav
│           │   ├── viewmodel/
│           │   │   └── MedsViewModel.kt     # UI state management
│           │   └── theme/
│           │       └── Theme.kt             # Material 3 theming
│           ├── notification/
│           │   ├── DoseAlarmScheduler.kt    # AlarmManager scheduling
│           │   ├── DoseReminderReceiver.kt  # Notification delivery
│           │   ├── OverdueDoseWorker.kt     # WorkManager fallback
│           │   ├── BootReceiver.kt          # Reboot alarm restore
│           │   └── SnoozeReceiver.kt        # 15-min snooze
│           └── util/
│               └── Helpers.kt               # Date/time utilities
├── gradle/
│   └── libs.versions.toml             # Centralised dependency versions
└── build.gradle.kts                   # Root build config
```

---

## System Requirements

| Requirement | Details |
|---|---|
| Android version | 8.0 (Oreo, API 26) or higher |
| Target | Android 15 (API 35) |
| Permissions | Notifications, exact alarms, boot completed, wake lock |
| Storage | ~15 MB installed |
| Network | Not required — fully offline |

---

## Prerequisites

- [Android Studio](https://developer.android.com/studio) Ladybug (2024.2) or later
- JDK 17
- Android SDK 35
- An Android device or emulator running API 26+

---

## Installation

### Clone

```bash
git clone https://github.com/rjamesy/Mymeds.git
cd Mymeds
```

### Build

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Install on Device

```bash
./gradlew installDebug
```

Or open the project in Android Studio and run on a connected device/emulator.

---

## Local Development

### Run

Open in Android Studio → select a device → click Run (or `Shift+F10`).

### Build

```bash
./gradlew assembleDebug        # Debug build
./gradlew assembleRelease      # Release build (requires signing config)
```

### Lint

```bash
./gradlew lint
```

### Tests

```bash
./gradlew test                 # Unit tests
./gradlew connectedAndroidTest # Instrumented tests
```

---

## Configuration

### Environment Variables

None required. The app is fully self-contained with no API keys, backend services, or remote configuration.

### Secrets

No secrets to manage. All data is stored locally on-device via Room (SQLite) and SharedPreferences.

### Database

Room database with automatic migrations. Schema version 2. The database is created automatically on first launch — no setup required.

**Tables:**
- `medications` — medication definitions, schedules, stock levels
- `dose_logs` — daily dose records with status tracking
- `stock_events` — stock change history

### SharedPreferences

- `theme_mode` — UI theme (system/light/dark)
- `notifications_enabled` — notification toggle

---

## Usage

### Adding a Medication

1. Tap the **+** button on the Dashboard
2. Enter medication name, dosage, and unit
3. Select frequency (daily, twice daily, etc.)
4. Set scheduled times for each dose
5. Optionally configure stock tracking and dose interval
6. Save

### Tracking Doses

- The Dashboard shows today's doses in chronological order
- Tap **Take** to mark a dose as taken (stock is automatically decremented)
- Tap **Skip** to skip a dose
- Missed doses are automatically flagged after their scheduled time

### Viewing History

- Switch to the **History** tab
- Browse the calendar to see adherence by day
- Tap a day to see individual dose details
- View 30-day summary statistics at the top

### Managing Stock

- Set initial stock when adding a medication
- Stock decrements automatically when doses are taken
- Use the stock adjustment dialog for manual corrections (refills, corrections)
- Low-stock warnings appear on the Dashboard when stock falls below threshold

### Backup & Restore

- **Settings → Export**: saves all data as a JSON file
- **Settings → Import**: restores from a previously exported JSON file
- **Settings → Clear All Data**: removes everything (requires two-step confirmation)

---

## Data Model

### Medication

| Field | Type | Description |
|---|---|---|
| id | Long | Primary key (auto-generated) |
| name | String | Medication name |
| dosage | String | Dosage amount |
| unit | String | Unit (mg, ml, etc.) |
| frequency | String | daily, twice_daily, three_times_daily, every_other_day, every_x_hours, weekly, as_needed |
| timesPerDay | Int | Number of doses per day |
| scheduledTimes | List\<String\> | Scheduled times (HH:mm format) |
| doseIntervalHours | Int | Minimum hours between doses (1–6) |
| tabletsPerDose | Int | Tablets consumed per dose |
| currentStock | Int | Current inventory count |
| lowStockThreshold | Int | Alert threshold |
| repeatsRemaining | Int | Refills remaining |
| notes | String | Free-text notes |
| active | Boolean | Whether medication is active |
| color | String | Hex colour for UI display |
| createdAt | Long | Creation timestamp |

### DoseLog

| Field | Type | Description |
|---|---|---|
| id | Long | Primary key |
| medicationId | Long | Foreign key to medication |
| scheduledDate | String | Date (yyyy-MM-dd) |
| scheduledTime | String | Time (HH:mm) or "PRN" |
| status | String | pending, taken, skipped, missed |
| takenAt | String? | ISO datetime when taken |
| createdAt | Long | Creation timestamp |

### StockEvent

| Field | Type | Description |
|---|---|---|
| id | Long | Primary key |
| medicationId | Long | Foreign key to medication |
| type | String | consumed, added, adjusted |
| quantity | Int | Amount changed |
| note | String | Description |
| createdAt | Long | Creation timestamp |

---

## Key Algorithms

### Dose Generation

Daily dose logs are generated dynamically based on medication frequency:
- **Daily/2x/3x daily**: creates logs for each scheduled time
- **Weekly**: only on the matching day of the week
- **Every other day**: uses date parity to determine active days
- **Every X hours**: generates from first scheduled time using the interval
- **As needed (PRN)**: available on-demand, not pre-scheduled

Reconciliation logic handles medication schedule changes — preserving taken/skipped statuses while updating pending dose times.

### Dose Rescheduling

When a dose is taken late, remaining doses for the day are shifted forward to maintain the minimum interval between doses. Only pending doses are affected.

### Drug Interaction Checking

Performs case-insensitive substring matching across all active medication names. O(n^2) pairwise comparison returns interactions sorted by severity (high → moderate → low).

---

## Notification System

Two-layer approach for reliability:

1. **AlarmManager** (primary) — schedules exact alarms for each dose. Provides precise timing on Android 12+.
2. **WorkManager** (fallback) — periodic 15-minute check catches any alarms that were missed (e.g., due to battery optimisation).

Additional receivers:
- **BootReceiver** — reschedules all alarms after device restart
- **SnoozeReceiver** — handles 15-minute snooze from notification actions

### Permissions

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

---

## Security & Privacy

- **No network access** — the app makes zero network requests
- **No analytics or tracking** — no data leaves the device
- **No accounts or sign-up** — no authentication required
- **Local-only storage** — all data in Room (SQLite) and SharedPreferences on-device
- **No third-party SDKs** collecting data
- **Export is user-initiated** — backup files are plain JSON stored where the user chooses

---

## Known Limitations

- Drug interaction database is curated but not exhaustive — not a substitute for professional medical advice
- Battery optimisation on some OEMs (Xiaomi, Samsung, Huawei) may delay or suppress notifications; users may need to whitelist the app
- No cloud sync — data exists only on the device
- No multi-user or sharing support
- Stock tracking requires manual refill entry

---

## Roadmap

- [ ] Google Play Store release
- [ ] Screenshots and demo GIF for README
- [ ] Medication photos/icons
- [ ] Recurring refill reminders
- [ ] Expanded drug interaction database
- [ ] Widget for home screen
- [ ] Wear OS companion
- [ ] CSV/PDF export for sharing with healthcare providers
- [ ] Multi-language support

---

## Troubleshooting

### Notifications Not Appearing

1. Ensure notifications are enabled in **Settings** within the app
2. Check Android system settings: **Settings → Apps → MyMeds → Notifications** must be enabled
3. On some devices, disable battery optimisation for MyMeds: **Settings → Battery → MyMeds → Unrestricted**
4. After a device restart, alarms are automatically rescheduled — open the app once to trigger this

### Doses Not Generating

- Ensure the medication is set to **Active** in Settings
- Check that scheduled times are configured for the medication
- Pull-to-refresh on the Dashboard to trigger regeneration

### Import Not Working

- Ensure the JSON file was exported from MyMeds (same schema version)
- The import replaces all existing data — export a backup first

---

## Contributing

Contributions are welcome.

### Branching

- `main` — stable branch
- Feature branches: `feature/description`
- Bug fixes: `fix/description`

### Pull Request Process

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes
4. Ensure the project builds: `./gradlew assembleDebug`
5. Run lint: `./gradlew lint`
6. Submit a pull request with a clear description of changes

### Coding Standards

- Kotlin with Jetpack Compose conventions
- MVVM architecture — UI logic in ViewModels, data logic in Repository
- Coroutines for async work (`Dispatchers.IO` for database/disk)
- Material 3 components and theming

---

## License

This project is licensed under the [MIT License](LICENSE).

---

## Acknowledgements

- [Jetpack Compose](https://developer.android.com/jetpack/compose) — modern Android UI toolkit
- [Room](https://developer.android.com/training/data-storage/room) — SQLite object mapping
- [Material 3](https://m3.material.io/) — design system
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) — reliable background work

---

## Disclaimer

MyMeds is a personal medication tracking tool. It is **not a medical device** and should **not** be used as a substitute for professional medical advice, diagnosis, or treatment. Always consult a healthcare provider for medical decisions. The drug interaction database is informational only and may not be complete or up to date.
