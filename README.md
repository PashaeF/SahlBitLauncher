# SahlBit Launcher 0.5.2

A Persian RTL Android launcher focused on simple, controlled interaction for older adults, children, and users who benefit from a simplified home screen.

## Build configuration

- **Application ID:** `ir.sahlbit.launcher`
- **Version:** `0.5.2`
- **Version Code:** `18`
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
- Direct calls from contact cards request `CALL_PHONE` permission at runtime; if permission is denied or direct calling fails, the system dialer is opened instead.
- Proximity monitoring uses Android's `PROXIMITY_SCREEN_OFF_WAKE_LOCK` when supported. The call activity does not hold the display on with `FLAG_KEEP_SCREEN_ON`.

Call behavior still depends on the selected default Dialer role, Android Telecom, carrier configuration, device firmware, and hardware support. Call waiting and proximity behavior require physical-device verification.

### Permissions and privacy
- Launcher preferences and card configurations are stored locally.
- Contact access requires the Android contacts permission.
- Direct calling requires the Android call permission.
- Screen-timeout modification requires Android's special `WRITE_SETTINGS` access.
- The About screen does not embed a personal phone number in the application source.
- No server-side account or synchronization service is included.

## Build with GitHub Actions

The repository workflow builds a debug APK using JDK 17 and Gradle 8.11.1. It runs automatically on pushes to `development` and `main`, and can also be started manually.

The uploaded artifact is named `sahlbit-debug-apk`.

## Verification status

Version 0.5.2 includes source-level compatibility work and the existing source-level fixes for page-layout persistence, home-screen drag prevention, call permission handling, call-waiting call selection, held-call switching, proximity screen-off handling, and clock update frequency.

A successful GitHub Actions build verifies compilation and artifact creation only. It does **not** certify runtime behavior on physical devices. The following must still be tested on supported devices, including Samsung One UI:

- Incoming and outgoing calls, including call waiting and switching between active/held calls.
- Proximity screen-off and wake behavior while the phone is held near the user's ear.
- Runtime permission grant/deny paths for direct calling.
- Page duplication, deletion, reordering, and page-count reduction with different per-page layouts.
- Confirmation that home cards cannot be reordered by long-pressing or dragging.
- Clock settings and responsive sizing on narrow screens.
- Contact photos and permission behavior.

Keep the device's existing system launcher available during testing. Do not remove the recovery path until the launcher and calling workflows have been verified on the target device.

## Version 0.5.2 changes

- Lowered the minimum Android version to API 21 (Android 5.0).
- Enabled core-library desugaring for `java.time` APIs on older Android versions.
- Disabled the custom `InCallService` and in-call activity below API 23 because Android added `InCallService` in Android 6.0; Android 5.0/5.1 uses the system phone app for the in-call interface.
- Added API 21-compatible call permission handling and legacy launcher icon resources.
- Guarded API 23+ call-direction, runtime-permission, settings, and package-query APIs.
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
