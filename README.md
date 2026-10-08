# Maid on Demand

Customer app for booking home-service professionals (cleaning, cooking, dishwashing), built with
Kotlin and Compose Multiplatform. UI, state, domain rules and the fake backend are shared code.

Journey: **splash → login → search → profile & slots → review & pay → confirmation → bookings → cancel**.

## Setup

Requires JDK 17+ and Android SDK 37. No backend or keys.

```
./gradlew :androidApp:assembleDebug        # APK: androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug         # install on a connected device
./gradlew :shared:testAndroidHostTest      # unit tests (JVM)
./gradlew :shared:koverHtmlReport          # coverage: shared/build/reports/kover/html/index.html
```

iOS: open `iosApp/` in Xcode on a Mac.

## Architecture

`shared/src/commonMain`: `domain` (models, rules, repository interfaces) → `data` (fake backend,
repositories, persistence) → `presentation` (ViewModels, one `StateFlow<UiState>` each) → `ui`
(Compose screens, navigation, theme). `di/AppContainer` wires it together.

- Composables render state and send events; they never call repositories.
- `BookingRepository.bookings` is the single source of truth and changes only after the backend
  confirms, so list and detail stay consistent.
- Slot availability is derived from bookings, so cancelling releases the slot.
- A new search cancels the previous one; Pay and Cancel are ignored while a request is in flight.
- Platform code is limited to a key-value store and the Android back handler.
- Persistence: bookings and customer details are saved as JSON in SharedPreferences / NSUserDefaults.

## Failure controls

Tap **Lab** on the Explore tab. Each scenario can be `Off`, `Next call` (fails once, then
recovers) or `Always`.

| Control | Effect |
|---|---|
| No results | Search returns nothing |
| Search failure | Search fails; retry available |
| Slot conflict | Slot is taken as you pay; slots refresh so you can pick again |
| Payment failure | Payment declined, no booking created, draft kept |
| Cancellation failure | Cancel fails; booking stays confirmed |

## Assumptions

- Times are shown in Asia/Kolkata; every visit is 60 minutes at a flat INR price.
- Mobile numbers are 10 digits starting with 6–9.
- Payment is simulated; no credentials are collected. Login is a demo step with no authentication.
- Cancelling before the start time refunds in full; at or after start it is refused.

## Tests

66 shared tests covering validation, stale results, booking success and failure, duplicate-action
prevention, cancellation state and navigation. Line coverage of the logic layers is 99.9% (Kover);
Compose UI is excluded from that figure.

## Verified platforms

Android: built, tests passing on the JVM and on a Pixel 9a. iOS: not built (developed on Windows).
