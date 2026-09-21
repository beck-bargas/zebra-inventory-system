<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="140" alt="TireScanner icon">

# TireScanner

**Barcode-driven tire inventory, built for a shop floor with no Wi-Fi guarantee.**

![Kotlin](https://img.shields.io/badge/Kotlin-2.3.10-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-SDK%2024--36-3DDC84?logo=android&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-9.1.0-02303A?logo=gradle&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-Offline--First-003B57?logo=sqlite&logoColor=white)
![Zebra](https://img.shields.io/badge/Zebra-DataWedge-000000?logo=zebratechnologies&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

![Retrofit](https://img.shields.io/badge/Retrofit-2.9.0-48B983?logo=square&logoColor=white)
![Gson](https://img.shields.io/badge/Gson-2.13.2-E34F26)
![Coroutines](https://img.shields.io/badge/Coroutines-1.10.2-7F52FF?logo=kotlin&logoColor=white)
![Glide](https://img.shields.io/badge/Glide-5.0.5-00B140)
![NanoHTTPD](https://img.shields.io/badge/NanoHTTPD-2.3.1-4B8BBE)
![Material](https://img.shields.io/badge/Material%20Components-1.13.0-757575?logo=materialdesign&logoColor=white)
![CI](https://img.shields.io/badge/CI-GitHub%20Actions%20%E2%86%92%20Firebase-2088FF?logo=githubactions&logoColor=white)

</div>

---

TireScanner is an Android inventory system for a working tire shop. Staff scan a tire's
barcode with a Zebra TC22 handheld, the app resolves it to a brand, model, and size, and
stock moves in or out in one motion — no typing, no laptop, and no dependence on the
building's internet connection.

It runs on real hardware in a real shop. The design constraints came from that: scans have
to register instantly, the app has to keep working when the network drops, and multiple
handhelds have to agree on what's in stock without a server in the middle.

---

## Core Features

1. **One-motion stock movement.** Pick IN or OUT on the home screen, then scan. Quantity
   updates immediately, with a running scan history and live inventory totals.
2. **Hardware barcode scanning.** Integrates with Zebra DataWedge over broadcast intents.
   The app configures the scanner profile programmatically and suspends scanner input while
   dialogs are open, so a stray trigger pull can't corrupt an in-progress edit.
3. **Two-tier barcode resolution with caching.** Unknown barcodes hit a paid lookup API,
   fall back to a free tier when that returns nothing usable, and land in a local cache
   table so the same tire is never looked up twice.
4. **Product title cleaning.** Vendor barcode databases return titles like
   `LT265/70R17 121/118S E BSW All-Terrain Tire (10 PLY)`. A normalization pass strips size
   codes, load ratings, ply counts, and marketing filler down to a usable brand and model.
5. **Offline-first storage.** Everything lives in local SQLite. Network access is an
   enhancement, never a requirement.
6. **Permanent 6-digit SKUs.** Every tire gets a stable internal SKU, searchable alongside
   brand, size, and barcode.
7. **Peer-to-peer LAN sync.** Each device runs a small embedded HTTP server, so two
   handhelds on the same network can reconcile inventory directly with each other.
8. **Web dashboard sync.** Push and pull against a remote dashboard, plus an append-only
   scan history feed for auditing who moved what.

---

## Screenshots

> _Add 2–3 screenshots here — the home screen with IN/OUT cards, the inventory list, and a
> scan result dialog._

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Zebra TC22 handheld                                    │
│                                                         │
│   DataWedge ──broadcast intent──►  MainActivity         │
│                                        │                │
│                                        ▼                │
│                                  TireRepository         │
│                                    │        │           │
│                    ┌───────────────┘        └────────┐  │
│                    ▼                                 ▼  │
│            SQLite (tires.db)                  Barcode   │
│            • tires                            lookup    │
│            • barcode_cache                    (2-tier)  │
│                    │                                    │
│         ┌──────────┴──────────┐                         │
│         ▼                     ▼                         │
│    SyncServer            SyncManager                    │
│    (NanoHTTPD,           (push / pull /                 │
│     token-auth)           history feed)                 │
└─────────┼─────────────────────┼─────────────────────────┘
          │                     │
          ▼                     ▼
   other handhelds        web dashboard
     on the LAN
```

### Sync design

The interesting problem here isn't the scanning, it's making several devices agree.

Every tire row carries a **`sync_id` UUID** generated on creation, independent of its
autoincrement primary key. Merges key on that UUID rather than on row IDs, so two handhelds
that each added tires offline can reconcile without collisions. Reconciliation is an upsert
of the incoming set followed by a delete of local rows whose `sync_id` isn't present,
keeping devices convergent rather than additive.

`SyncServer` is a NanoHTTPD instance running on the device itself, exposing
`GET /inventory` and `POST /sync`. Every request must carry a shared token header or it's
rejected with a 403. That token is injected at build time and is never committed.

### Schema migrations

`tires.db` is on schema version 6, with each upgrade applied incrementally in `onUpgrade` —
adding `sync_id` and backfilling UUIDs for existing rows, introducing the barcode cache
table and seeding it from existing inventory, and so on. Shop devices in the field upgrade
in place without losing data.

---

## Tech Stack

| Layer | Choice | Version |
|---|---|---|
| Language | Kotlin | 2.3.10 |
| Build | Gradle / Android Gradle Plugin | 9.1.0 / 9.0.1 |
| Storage | SQLite via `SQLiteOpenHelper` | schema v6 |
| Async | Kotlin Coroutines | 1.10.2 |
| HTTP client | Retrofit + Gson | 2.9.0 / 2.13.2 |
| Embedded server | NanoHTTPD | 2.3.1 |
| Images | Glide | 5.0.5 |
| UI | Android Views + Material Components | 1.13.0 |
| Scanner | Zebra DataWedge intent API | — |
| CI/CD | GitHub Actions → Firebase App Distribution | — |

Min SDK 24, compile/target SDK 36.

---

## Getting Started

```bash
git clone https://github.com/beck-bargas/zebra-inventory-system.git
cd zebra-inventory-system
```

Create a `local.properties` file in the project root (it's gitignored):

```properties
sdk.dir=/path/to/Android/sdk
BARCODE_API_KEY=your_barcode_lookup_api_key
SYNC_TOKEN=a_shared_secret_for_device_sync
```

Both values are surfaced through `BuildConfig` at compile time — no credentials live in
source. Then:

```bash
./gradlew assembleDebug
```

The app targets Zebra devices running DataWedge. On other hardware the UI works but
hardware scanning is unavailable; manual entry covers that case.

---

## CI/CD

Pushes to `master` trigger a GitHub Actions workflow that writes `local.properties` from
repository secrets, builds a debug APK, and distributes it to the `testers` group through
Firebase App Distribution — so shop devices get new builds without anyone sideloading
anything.

---

## Known Limitations

Worth stating plainly rather than leaving for someone to discover:

- **Dashboard sync runs over plain HTTP.** The shared token protects the endpoint but
  travels unencrypted. Migrating the dashboard to HTTPS and removing the app's blanket
  cleartext permission is the top outstanding item.
- **Auth is a single shared token**, not per-device credentials. Fine for a handful of
  handhelds in one building; it wouldn't survive more than that.
- **Last-write-wins on conflicts.** If two devices edit the same tire offline, the later
  sync overwrites. Real conflict resolution would need per-row versioning.
- **No automated tests.** The logic most worth covering is the title-normalization regex
  and the merge reconciliation.

---

## License

MIT
