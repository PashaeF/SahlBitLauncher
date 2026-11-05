# SahlBit Launcher 0.5.4

A Persian RTL Android launcher focused on simple, controlled interaction for older adults, children, and users who benefit from a simplified home screen.

## Build configuration

- **Application ID:** `ir.sahlbit.launcher`
- **Version:** `0.5.4`
- **Version Code:** `20`
- **Minimum Android:** API 21 (Android 5.0)
- **Compile SDK / Target SDK:** 35
- **JDK:** 17
- **Gradle:** 8.11.1

## Main features

### Home screen
- Persian RTL interface with a configurable analog clock.
- Clock size and digit language/size settings.
- Second-hand updates are suspended when the second hand is disabled.
- Battery and charging indicators.
- Multiple home pages with swipe navigation.
- Contact and app cards with configurable rows, card counts, and per-row alignment.
- **Home-screen card reordering by long-press and drag is intentionally disabled.** Card order can only be changed from the PIN-protected management area.
- The home screen does not expose a drag handle or gesture that can modify card positions.

### Management
- PIN-protected management dashboard.
- Create, duplicate, rename, delete, and reorder pages.
- Per-page row count, per-row card count, and per-row left/center/right alignment.
- Page layout preferences are remapped together with page operations:
  - Reordering swaps the corresponding layout settings.
  - Duplicating a page copies its layout settings.
  - Deleting a page removes its settings and shifts the remaining settings.
  - Reducing page count removes settings for deleted pages.
- Add contacts and choose one phone number when a contact has multiple numbers.
- Add installed launchable apps to selected pages.
- Change contact-card photos and reorder cards from the management screen.

### Calling and Telecom
- Android Telecom `InCallService` integration.
- Incoming calls are prioritized over existing active calls so call waiting is visible.
- Answer/reject controls for ringing calls.
- Active/held call switching controls.
- Contact name, number, and available contact photo display.
- Dedicated full-screen call activity with large answer, reject, cancel, speaker, and hang-up controls.
- Call-state colors: softened blue for incoming ringing, amber for outgoing dialing, and green for connected calls.
- Speaker switching and proximity screen-off during an active call.
- Contact photos can be sourced from the Android contacts record or the gallery.
- Direct calls from contact cards request `CALL_PHONE` permission at runtime; if permission is denied or direct calling fails, the system dialer is opened instead.
- SahlBit must be selected as the Android default Dialer for its custom in-call interface to be used. Android 5.0/5.1 continues to use the system call interface.
- Proximity monitoring uses Android's `PROXIMITY_SCREEN_OFF_WAKE_LOCK` when supported. The call activity does not hold the display on with `FLAG_KEEP_SCREEN_ON`.

Call behavior still depends on the selected default Dialer role, Android Telecom, carrier configuration, device firmware, and hardware support. Call waiting and proximity behavior require physical-device verification.

### Permissions and privacy
- Launcher preferences and card configurations are stored locally.
- Contact access requires the Android contacts permission.
- App usage ordering can use Android Usage Access; it is optional and can be granted from the app catalog.
- Direct calling requires the Android call permission.
- Screen-timeout modification requires Android's special `WRITE_SETTINGS` access.
- The About screen displays the contact number configured by the project owner.
- No server-side account or synchronization service is included.

## Build with GitHub Actions

The repository workflow builds a debug APK using JDK 17 and Gradle 8.11.1. It runs automatically on pushes to `development` and can also be started manually.

The uploaded artifact is named `sahlbit-debug-apk`.

## Verification status

Version 0.5.3 includes the page-layout persistence and drag-prevention work, global Vazirmatn typography, page-scoped contact/app management, app search and usage-based ordering, configurable clock and timeout controls, and the redesigned Telecom call interface. Source-level completion does not replace physical-device testing.

A successful GitHub Actions build verifies compilation and artifact creation only. It does **not** certify runtime behavior on physical devices. The following must still be tested on supported devices, including Samsung One UI:

- Incoming and outgoing calls, including call waiting and switching between active/held calls.
- Proximity screen-off and wake behavior while the phone is held near the user's ear.
- Runtime permission grant/deny paths for direct calling.
- Page duplication, deletion, reordering, and page-count reduction with different per-page layouts.
- Confirmation that home cards cannot be reordered by long-pressing or dragging.
- Clock settings and responsive sizing on narrow screens.
- Contact photos and permission behavior.

Keep the device's existing system launcher available during testing. Do not remove the recovery path until the launcher and calling workflows have been verified on the target device.

## Version 0.5.4 changes

- Updated to versionCode 20 / versionName 0.5.4.
- Added configurable hour, minute, and second hand lengths and an in-settings analog clock preview.
- Removed unused phone-state and vibration permissions to keep the requested access surface smaller.
- Redesigned the home charging indicator and the dedicated call interface; removed SahlBit-posted call notifications.
- Improved administrator login, contact-card management, app search, and page-capacity feedback.
- Added volume controls for calls, media, alarms, and notifications, plus ringtone/notification/alarm tone pickers.
- Localized timeout and brightness values in Persian digits and set cream as the default background.

## Version 0.5.3 changes

- Updated version to 0.5.3 (versionCode 19); minimum Android remains API 21 (Android 5.0).
- Applied Vazirmatn to Compose typography, native theme widgets, dial pad, and custom call interface.
- Improved Dialer role discovery with the required ACTION_DIAL intent filter and the dedicated full-screen call interface without SahlBit-posted call notifications.
- Redesigned the dedicated call screen with large senior-friendly controls, call-state colors, mute, speaker, hold/resume, call waiting, and proximity handling.
- Scoped selected contacts and apps to the currently selected home page; removed duplicate phone-number choices and added contact-photo source selection.
- Added search and Android usage-time sorting to the installed-app catalog; system apps are listed first.
- Replaced clock size steppers with sliders, defaulted the second hand off, added calming background choices, and changed screen timeout to minute/second numeric fields.
- Defaulted numeric battery percentage and app/contact labels to off.
- Added a phone contact number to the About page and increased its typography.
- Enabled R8/resource shrinking for the debug APK and removed unused Compose tooling dependencies.
- Restricted GitHub Actions builds to the `development` branch.

## Version 0.5.1 changes

- Updated version to 0.5.1 (versionCode 17).
- Fixed page-layout preference remapping for page reorder, duplication, deletion, and page-count reduction.
- Removed all home-screen card drag-and-drop handling; card order is managed only in the admin settings.
- Added runtime `CALL_PHONE` permission handling for direct contact calls with a safe dialer fallback.
- Prioritized ringing calls over active calls in the Telecom service.
- Added active/held call switching controls.
- Replaced the black proximity overlay and persistent screen-on flag with a proximity screen-off wake lock when supported.
- Reduced clock updates to once per minute when the second hand is disabled.
- Constrained the displayed clock size to the available screen width.
- Updated GitHub Actions to build on pushes to `development`.
- Refreshed project documentation and removed the embedded phone number from the About screen.
