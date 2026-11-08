# SahlBit Launcher 0.6.0

A Persian RTL Android launcher focused on simple, controlled interaction for older adults, children, and users who benefit from a simplified home screen.

## Build configuration

- **Application ID:** `ir.sahlbit.launcher`
- **Version:** `0.6.0`
- **Version Code:** `26`
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
- Contact name, number, and available contact photo display.
- Dedicated full-screen call activity with configurable answer, reject, cancel, speaker, and hang-up controls. Outgoing dialing displays exactly one red end-call button; there is no second button before the call connects.
- Call-state colors and all three call layouts can be customized by the administrator, including image size/radius, typography, button labels/sizes/radius/spacing, and colors.
- Call customization previews can use a contact already saved to the launcher, including its selected photo.
- Speaker route changes are reflected after Telecom reports the updated audio route.
- Proximity screen-off and wake behavior during an active call.
- Contact photos can be sourced from the Android contacts record or the gallery.
- Direct calls from contact cards request `CALL_PHONE` permission at runtime; if permission is denied or direct calling fails, the system dialer is opened instead.
- SahlBit must be selected as the Android default Dialer for its custom in-call interface to be used. Android 5.0/5.1 continues to use the system call interface.
- Proximity monitoring uses Android's `PROXIMITY_SCREEN_OFF_WAKE_LOCK` when supported. The call activity does not hold the display on with `FLAG_KEEP_SCREEN_ON`.

Call behavior still depends on the selected default Dialer role, Android Telecom, carrier configuration, device firmware, and hardware support. Call waiting and proximity behavior require physical-device verification.

### Permissions and privacy
- Contacts and direct-call permissions are requested only after a user-initiated action; neither permission is silently granted by SahlBit.
- The contacts-management and call-customization screens expose explicit permission status/actions so users can grant access when they choose.
- Launcher preferences and card configurations are stored locally.
- Contact access requires the Android contacts permission.
- App usage ordering can use Android Usage Access; SahlBit opens the package-specific Settings entry where supported. Exact focus/scroll behavior inside Android Settings is controlled by the device manufacturer and cannot be guaranteed by a third-party app.
- Direct calling requires the Android call permission.
- Screen-timeout modification and saving custom ringtone choices require Android's special `WRITE_SETTINGS` access.
- Volume sliders write to Android `AudioManager` streams and refresh from system volume-change events and when the app resumes.
- Android exposes a shared `STREAM_VOICE_CALL` volume path for earpiece and speakerphone; the two call-volume controls are intentionally linked rather than pretending to be independent.
- Alarm volume uses the separate `STREAM_ALARM` stream and is not the same as ringtone volume.
- The About screen displays the contact number configured by the project owner.
- No server-side account or synchronization service is included.

## Build with GitHub Actions

The repository workflow always builds a test APK using JDK 17 and Gradle 8.11.1. If persistent signing secrets are configured, it also builds a signed release APK. It runs on pushes to `development` and can also be started manually.

Artifacts:
- `sahlbit-debug-apk`: test build; its debug signing key can change between hosted runners.
- `sahlbit-release-apk`: stable-signed build, available after the four signing secrets are configured.

A separate tag-triggered workflow publishes a signed GitHub Release when a `v*` tag is pushed.

## Verification status

Version 0.6.0 (versionCode 26) has been built successfully by GitHub Actions from the `development` branch. Build #342 completed the debug APK build and artifact upload. The downloaded APK was checked for ZIP integrity, expected package resources (including the transparent SahlBit logo), DEX payloads, and a valid Android debug signature. Its SHA-256 is recorded in `update.json`.

These checks confirm source compilation and APK packaging; they do **not** certify runtime behavior on a physical device. No emulator or physical Samsung device was available to execute the app during this build review. Before promoting 0.6.0 to `main`, verify the following on Android 13 / Samsung One UI and at least one older supported Android version:

- Clean install, first launch, relaunch after process death, and recovery after an intentional crash.
- Setup stages 1–7: return to the same stage after Android Settings, live permission-state refresh, skip paths, and preservation of an existing administrator PIN.
- Contacts and direct-call permissions are requested only after an explicit user action; denial leaves the remaining launcher features usable.
- Default Home and Dialer role selection, call confirmation, incoming/outgoing calls, call waiting, speaker routing, and proximity screen-off/wake.
- Home layout and live preview with 1–6 rows, 1–6 cards per row, different row alignments, multiple pages, and page duplication/deletion/reordering.
- Contact/app image clipping, separate corner-radius settings, missing contact names, and saved-contact selectors.
- All ten Quick Settings tiles on Android 7.0 (API 24) or newer. Android 5/6 do not provide the Quick Settings tile API.
- App details/launch buttons, Android settings shortcuts, cache clearing, uninstall hand-off, and updater flows.
- Website update source at `https://pashaef.ir/sahlbit/update.json`. The GitHub update source currently targets `main`; it will return 0.6.0 only after the tested version is promoted to that branch.

The debug APK is signed with a runner-generated Android debug key. Its certificate can differ between builds, so installing it over an APK signed with another key may require uninstalling the older copy first. A persistent release signing key must be configured before distributing stable signed updates.

## Version 0.5.9 changes

- Removed the remaining vertical offset from the first-run logo so it is centered in its blue circle.
- Made first-run setup completion atomic: after the final step, the completion flag is saved before PIN setup, and an existing administrator PIN always routes the app to home rather than reopening setup.
- Rechecked each setup-step permission when the step is displayed and retained a distinct unknown state when a permission query fails.
- Corrected outgoing-call customization: the pre-connection screen and live preview contain one red “پایان تماس” button only. The second button and its label/color controls are removed for this state; the remaining spacing control adjusts the single button's distance from the contact details.
- Matched the outgoing button's configured color, label, and spacing between preview and the actual call screen; corrected the vertical space reserved for the speaker control to keep call buttons within the screen.
- Added a GitHub Contents API fallback if the raw update manifest cannot be fetched or parsed; kept update status messages in Persian and corrected the counterclockwise refresh icon in both source buttons.
- Replaced the last informal administrator-facing phrases and removed an obsolete unused welcome-screen composable.
- This release continues to target Android 5.0 (API 21) and newer.


## Version 0.5.8 changes

- Corrected the website update-manifest address to the project's pashaef.ir domain.
- Centered the launcher logo in the About page.
- Replaced the clock-category calendar glyph with a clock icon and the audio-category bell with a volume icon.
- Corrected the remaining unflipped search icon and increased the top spacing on the administrator PIN setup screen.
- This release continues to target Android 5.0 (API 21) and newer.

## Version 0.5.7 changes

- Prevented completed first-run setup from reopening after process death, task dismissal, or navigation back; existing administrator PINs are preserved.
- Added a three-state permission status (granted, not granted, and unavailable) and corrected package-specific Usage Access Settings navigation.
- Added ten SahlBit Quick Settings tiles on Android 7.0+; each tile starts the launcher home screen. Android 5 and 6 remain supported, with the tiles disabled on those versions.
- Added a dedicated contact-confirmation dialog customization page with live preview for button colors, labels, height, text size, contact-image size/radius, button radius, and vertical spacing.
- Added a two-button outgoing-call customization preview and clarified upper/lower button color labels.
- Merged the shared earpiece/speaker call-volume control into one slider and display current/max volume values.
- Standardized numeric slider width, preserved the management dashboard scroll position, and aligned home rows to fixed columns.
- Improved unnamed contact cards, home empty-state alignment, weekday indicators, and call-confirmation button spacing.
- Removed scrolling from the full-screen call activity and made its avatar size responsive to available screen height; cropped preview photos to avoid exposed background bands.
- Changed GitHub update checks to use the repository's shared update.json manifest. The same JSON schema is used by the website update endpoint at https://pashaef.ir/sahlbit/update.json.
- Updated the app version to 0.5.7 (versionCode 23). Minimum Android remains API 21 (Android 5.0).

## Version 0.5.5 changes

- Added a seven-page first-run setup flow: welcome/logo, default launcher, system-settings access, default Dialer, contacts, direct calling, and Usage Access. Each permission step explains its purpose, supports skipping, resumes after Android Settings, and remains available in the permissions page.
- Existing 0.5.4 installations see the new setup flow once; completing it does not reset the administrator PIN. New installations set the PIN after completing the setup flow.
- Added three call customization profiles (incoming ringing, outgoing dialing, and connected call) with editable labels, sizes, spacing, corner radii, color swatches, and a live preview using a saved launcher contact.
- Added asynchronous speaker-route UI refresh and return-to-home behavior after calls end.
- Equalized home-card dimensions independent of contact photo/app icon and aligned the page-layout preview with the home-card sizing calculation.
- Added app reorder/remove controls and contact permission controls in both the contact manager and permissions page.
- Added separate permission cards with explanations and current status; Android Settings is now a distinct section.
- Opened Usage Access using the package-specific URI supported by Android's Usage Access Settings activity.
- Applied screen-timeout changes automatically after input and made settings/PIN pages scroll with the on-screen keyboard.
- Set clock defaults to 360 dp, 40 sp, English digits, 40%/65%/90% hand lengths, and no second hand.
- Added system volume control in ring, notification, media, system, voice-call, and alarm order; refreshed values from Android and clarified shared earpiece/speaker call volume.
- Redesigned About and the administrator setup/login layouts.

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

## Signing and distribution status

- The 0.6.0 development builds are debug-signed because the previous signing keystore and its passwords are not available.
- Debug builds generated by GitHub Actions may use different debug certificates. They are not guaranteed to update over one another, and they are not suitable as a stable Play Store / Cafe Bazaar / Myket release signature.
- A new persistent release keystore must be created and stored securely in GitHub Actions secrets before publishing a signed release. This new key will not match any APK signed with the unavailable previous key.
- The `update.json` SHA-256 field must be filled only after the final APK has been built and independently hashed; never publish an empty or guessed hash as a verified release.
