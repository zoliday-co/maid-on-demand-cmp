# Maid on Demand

A customer app for booking home-service professionals (cleaning, cooking, dishwashing), built with
Kotlin and Compose Multiplatform. UI, state management, domain rules and the fake backend are all
shared code; Android and iOS each contribute an entry point and a key-value store.

Journey: **search → profile & slots → review & pay → confirmation → bookings → cancel**.

## Setup

Requirements: JDK 17+, Android SDK 37 (path in `local.properties`). No backend, keys or accounts.

```
./gradlew :androidApp:assembleDebug        # APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
./gradlew :androidApp:installDebug         # install on a connected device / emulator
./gradlew :shared:testAndroidHostTest      # shared-code tests on the JVM
```

iOS: open `iosApp/` in Xcode on a Mac and run. Minimum Android version is 10 (API 29).
The app follows the system light / dark theme.

## Architecture

```
shared/src/commonMain/.../mainondemand
├── domain/         models, repository interfaces, rules (validation, cancellation, IST time, INR)
├── data/           FakeBackend (seed, latency, faults, persistence) + repository implementations
├── presentation/   ViewModels exposing one immutable StateFlow<UiState> each
├── ui/             theme, components, screens, navigation
├── di/             AppContainer — manual dependency graph
└── App.kt          shared entry point: App(container)
```

- **Unidirectional flow.** Composables render a `UiState` and call ViewModel functions; they never
  call repositories. ViewModels depend only on the `domain` interfaces.
- **Single source of truth.** `BookingRepository.bookings` is one `StateFlow` that the list, detail
  and confirmation screens all observe, and it changes only after the backend confirms a change —
  so a failed cancellation cannot leave any screen showing "cancelled".
- **Slot availability is derived, not stored**: a slot is open unless a confirmed booking, the seeded
  "busy" pattern or a simulated conflict covers it. Cancelling therefore releases the slot by
  construction. `availabilityVersion` bumps on every change and the search and profile ViewModels
  re-fetch when it does.
- **Stale results.** A new search cancels the in-flight one, and a response is applied only if its
  criteria still match the screen's.
- **Duplicate submissions.** Pay and cancel are ignored while a request is in flight (and after a
  payment has succeeded); the backend also rejects a second booking of the same slot.
- **Navigation** is a small back stack held in a ViewModel (`ui/navigation/Navigator.kt`). Each entry
  is its own `ViewModelStoreOwner`, so a screen's ViewModels — and their coroutines — are cancelled
  when it is popped, and survive rotation. Home's ViewModels live for the whole session, which is
  what preserves the search criteria.
- **Platform boundaries**: `KeyValueStore` (SharedPreferences / NSUserDefaults) and
  `PlatformBackHandler` (Android system back; no-op on iOS). Nothing else is platform-specific.

### Persistence

Bookings, simulated slot conflicts, and the customer's name / mobile / address / payment method are
serialised to JSON in the platform key-value store and restored on launch. A key-value store was
chosen over a database because the data is one small document owned by a fake backend; the
`KeyValueStore` interface keeps that swappable. The half-chosen slot and the back stack are
deliberately not restored after process death — a slot goes stale quickly.

## Failure controls

Tap **Lab** (top-right of the Explore tab) to open the Failure lab. Each scenario can be `Off`,
`Next call` (fails once, then recovers — so the retry succeeds) or `Always`. The dot on the button
turns red while anything is armed. Settings reset when the app process restarts.

| Control | What happens | Where to see it |
|---|---|---|
| No results | Search returns an empty list | Explore → empty state |
| Search failure | Search throws a network error | Explore → error state with **Try again** |
| Slot conflict | Another customer takes your slot as you pay | Review & pay → dialog → back on the profile with slots refreshed and a notice |
| Payment failure | Payment is declined; no booking is created | Review & pay → banner, details kept, **Retry payment** |
| Cancellation failure | Cancelling throws a network error | Booking details → dialog shows the error and **Try again**; booking stays confirmed |

Suggested failure-and-retry demo: arm *Payment failure → Next call*, book a slot, see the failure,
tap **Retry payment**.

## Seed data

Ten professionals across three Bengaluru localities (Indiranagar, Koramangala, HSR Layout) covering
all three services; hourly slots 8 AM–7 PM IST for the next 14 days with a deterministic share
already taken; three bookings created on first launch — one confirmed for tomorrow, one cancelled
(refunded) and one confirmed yesterday, which shows that a started booking cannot be cancelled.

## Assumptions

- All times are scheduled and shown in Asia/Kolkata regardless of the device time zone.
- A visit is exactly 60 minutes and the price is a flat INR amount per visit, per service.
- Search needs a locality, service and date; time is optional ("Any time"), rating and price are
  optional filters. A professional appears only if they have an open slot matching the criteria.
- A valid mobile number is 10 digits starting with 6–9; a pasted `+91` or leading `0` is stripped.
- Payment is simulated: the customer picks UPI or Card and no credentials are requested.
- Cancelling a confirmed booking before its start refunds 100%. At or after the start time
  cancellation is refused, on the client and again in the backend.
- Single customer, no authentication (out of scope). The splash and login screens are presentational:
  the mobile number is optional and **Log in** always continues to Home. They are shown on every
  cold start, and back from Home exits the app rather than returning to them.

## Tests

```
./gradlew :shared:testAndroidHostTest        # 66 tests on the JVM
./gradlew :shared:connectedAndroidDeviceTest # the same tests on a connected device
./gradlew :shared:koverHtmlReport            # coverage: shared/build/reports/kover/html/index.html
./gradlew :shared:koverLog                   # coverage summary in the console
```

66 tests in `shared/src/commonTest`, all passing on the JVM and on a Pixel 9a. They run the real
fake backend and repositories on virtual time.

- **Validation** — name, mobile and address rules; mobile sanitising.
- **Stale results** — a slow earlier search cannot overwrite a newer one.
- **Booking success and failure** — confirmed booking takes the slot; a failed payment creates
  nothing, keeps the draft and can be retried; slot conflict; double-booking; past slots.
- **Duplicate-action prevention** — repeated Pay / Cancel taps reach the backend once.
- **Cancellation state** — refund, slot release, list and detail change only after success, a failed
  cancel changes nothing, a started booking is refused.
- **Other** — navigation back stack, splash timer, filters, background refresh, error and retry
  paths, corrupt saved state, IST formatting, INR grouping, persistence across a simulated restart.

**Coverage (Kover):** 99.9% of lines, 84.5% of branches and 100% of classes in the logic layers —
`domain`, `data`, `presentation`, `di`, navigation and formatting. Composable UI (`ui/screens`,
`ui/components`, `ui/theme`, `App.kt`) and the SharedPreferences wrapper are excluded from the
report because unit tests cannot reach them; they have no automated tests.

## Verified platforms

- **Android**: `assembleDebug` builds and the shared tests pass on the JVM. Installed on a Pixel 9a,
  where splash, login and the Explore screen were checked; the booking, payment and cancellation
  screens still need a manual run-through on a device.
- **iOS**: not compiled or run — this was developed on Windows, which cannot build the iOS targets.
  The iOS source set contains only the `NSUserDefaults` store, the no-op back handler and
  `MainViewController`.

## Known gaps

- No recording of the core journey is included yet.
- No UI / screenshot tests; coverage stops at the ViewModel and navigation layer.
- The back stack is not restored after process death, and iOS has no swipe-back gesture.
- Fault settings and the slot draft are in-memory only.
- No instant or recurring bookings (optional extensions).
- The fake backend does its small JSON read/write on the calling dispatcher rather than an IO one.
- Text is English only and not yet extracted into string resources.
