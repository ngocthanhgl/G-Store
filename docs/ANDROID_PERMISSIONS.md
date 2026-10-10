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
| POST_NOTIFICATIONS (33+) | NOT implemented at M6 — progress is observed in-app from Room; no system notification is posted | — | — | — | n/a (no fallback needed) |
| FOREGROUND_SERVICE_* | NOT needed — CoroutineWorker without a foreground service; no FGS permission declared | — | — | — | n/a |
| ACCESS_NETWORK_STATE | NOT declared — WorkManager `NetworkType.CONNECTED` constraint used instead of manual connectivity inspection | — | — | — | n/a |

*Google Play policy risk documented in SECURITY_MODEL.md; F-Droid/sideload path unaffected.
Never requested: MANAGE_EXTERNAL_STORAGE, QUERY_ALL_PACKAGES, camera/mic/
contacts/location, accessibility, VPN, device-admin, BOOT_COMPLETED (WorkManager
persistence covers restart-resume without it). Storage: app-specific internal +
cache only; user export via system picker (MediaStore/SAF). APK share via
FileProvider `content://` URI, `application/vnd.android.package-archive`,
`FLAG_GRANT_READ_URI_PERMISSION`. Target-37: verify MeteredManager/local-net
behavior for on-device flows at M6; test adaptive layouts under resize on
sw>600dp (no fixed-orientation reliance).

Test cases per permission: grant → feature works; deny → fallback shown;
deny+don't-ask → deep-link to settings; upgrade targetSdk → re-verify.

## M6 verification (2026-10-10, `aapt d badging` on app-debug.apk)

- Declared by us: `INTERNET`, `REQUEST_INSTALL_PACKAGES` only.
- Merged transitively from WorkManager (normal protection, auto-granted at
  install, required for its restart-resume + constraint tracking):
  `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`. Justified by
  the M6 downloader decision; no code of ours touches them directly.
- `FOREGROUND_SERVICE` (also merged from WorkManager) is stripped via
  `tools:node="remove"`: no foreground service is ever started (plain
  CoroutineWorker), so the permission must not ship speculatively.
- `dev.gstore.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: our own
  signature-level dynamic-receiver guard, standard practice.
- No storage, notification, camera/mic/location permissions anywhere.
- `uses-feature`: `android.hardware.faketouch` only (touchscreen implied).
  `sdkVersion 26`, `targetSdkVersion 37`, launchable MainActivity present.
- FileProvider `dev.gstore.app.apk-provider` serves `filesDir/apks/` only.
