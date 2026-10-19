# ANDROID_PERMISSIONS — living matrix (review at every milestone)

Target: API 37 (Android 17, stable 2026-06-16). Target-37 notes verified
2026-10-10 against developer.android.com: large-screen resizability can no
longer be opted out (sw>600dp activities always resizable; games exempt);
local network blocked by default; MessageQueue lock-free (no reflection);
no static-final reflection; DCL of native libs requires read-only files.

| Permission / op | Why | Manifest | Grant | Optional? | Denial behavior |
|---|---|---|---|---|---|
| INTERNET | GitHub API + APK fetch | normal | install-time | No | offline-cached mode, banner |
| REQUEST_INSTALL_PACKAGES | system installer handoff | normal-ish* | settings page per-source (API 26+) | Yes | explanatory gate, retry entry point |
| REQUEST_DELETE_PACKAGES | request system uninstall confirmation for an app installed from G Store | normal | install-time | Yes | uninstall action reports failure; app remains installed |
| QUERY_ALL_PACKAGES | Installed shelf compares only package names stored in G Store download records; it does not enumerate or display a device-wide app list | manifest declaration; install-time visibility, no runtime prompt | Install-time | Optional to the shelf | download history remains available without installed-state checks |
| POST_NOTIFICATIONS (33+) | Update alerts and APK download progress/completion notifications | manifest declaration | Runtime request (API 33+) after first-launch explanation | Yes | downloads and update checks still work in-app; system notifications are omitted |
| FOREGROUND_SERVICE_* | NOT needed — CoroutineWorker without a foreground service; no FGS permission declared | — | — | — | n/a |
| ACCESS_NETWORK_STATE | NOT declared — WorkManager `NetworkType.CONNECTED` constraint used instead of manual connectivity inspection | — | — | — | n/a |

*Google Play policy risk documented in SECURITY_MODEL.md; F-Droid/sideload path unaffected.
QUERY_ALL_PACKAGES is Play-restricted and has no user-facing runtime grant dialog. A Play listing needs a policy declaration; G Store does not inspect unrelated installed packages.
Never requested: MANAGE_EXTERNAL_STORAGE, camera/mic/
contacts/location, accessibility, VPN, device-admin, BOOT_COMPLETED (WorkManager
persistence covers restart-resume without it). Storage: app-specific internal +
cache only; user export via system picker (MediaStore/SAF). APK share via
FileProvider `content://` URI, `application/vnd.android.package-archive`,
`FLAG_GRANT_READ_URI_PERMISSION`. Target-37: verify MeteredManager/local-net
behavior for on-device flows at M6; test adaptive layouts under resize on
sw>600dp (no fixed-orientation reliance).

Test cases per permission: notification grant → update/progress notifications
appear; notification denial → in-app download progress remains available;
install-source grant → pending APK handoff resumes only after the app re-checks
the system setting; denial → install remains blocked and the download stays
available. Re-verify after targetSdk upgrades.

Uninstall handoff: `Intent.ACTION_UNINSTALL_PACKAGE` with a `package:` URI
launches Android's confirmation UI; `REQUEST_DELETE_PACKAGES` is required on
Android 9+ and is a normal permission. The app never removes a package silently.

Installation handoff: the app explains the source-specific grant before
opening `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` on explicit user action.
On return to the resumed activity it re-checks
`PackageManager.canRequestPackageInstalls()`; only a confirmed grant resumes
the pending APK handoff to Android's package installer. The explanation UI
respects safe drawing insets. Denial or cancellation leaves the download
available and does not initiate installation.

## Permission onboarding

The first launch explains notification use and Android's separate per-source
APK install setting before presenting either system UI. Notification access is
requested only on API 33+ and only after the user selects Continue. APK
installation settings open only after a separate explicit confirmation.
Package visibility has no runtime prompt; the installed shelf queries only
package names recorded by G Store downloads.

## M6 verification (2026-10-10, `aapt d badging` on app-debug.apk)

- Declared by us: `INTERNET`, `REQUEST_INSTALL_PACKAGES`,
  `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES`, `POST_NOTIFICATIONS`.
- Merged transitively from WorkManager (normal protection, auto-granted at
  install, required for its restart-resume + constraint tracking):
  `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`. Justified by
  the M6 downloader decision; no code of ours touches them directly.
- `FOREGROUND_SERVICE` (also merged from WorkManager) is stripped via
  `tools:node="remove"`: no foreground service is ever started (plain
  CoroutineWorker), so the permission must not ship speculatively.
- `dev.gstore.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: our own
  signature-level dynamic-receiver guard, standard practice.
- No storage, camera/mic/location permissions.
- `uses-feature`: `android.hardware.faketouch` only (touchscreen implied).
  `sdkVersion 26`, `targetSdkVersion 37`, launchable MainActivity present.
- FileProvider `dev.gstore.app.apk-provider` serves `filesDir/apks/` only.
