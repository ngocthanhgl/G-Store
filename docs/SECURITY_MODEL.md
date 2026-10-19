# SECURITY_MODEL — G Store

Trust stance: repos, release notes, READMEs, filenames, asset metadata, and APK
bytes are UNTRUSTED input. GitHub hosting ≠ safety; stars ≠ trust.

## 1. Transport & secrets

HTTPS only; redirect allowlist (`github.com`, `*.githubusercontent.com`,
`*.githubassets.com`); no cleartext, no custom trust managers. No credentials,
tokens, or client secrets in the APK. User-supplied tokens (optional auth) live
in EncryptedSharedPreferences/Keystore, never prefs XML or logs.

## 2. Content handling

- Markdown → sanitized Compose model: no scripts, no `javascript:` URLs, strict
  inline-HTML subset (unsupported tags stripped, text kept); images load
  automatically only from HTTPS GitHub hosts, with size caps; other images
  require an explicit tap so README content cannot silently probe private
  networks; over-2 MB docs truncated with notice;
  relative links resolved against repo context; external links open via explicit
  user tap in the system browser/custom tab.
- filenames: sanitized (no path traversal, no absolute paths, length-capped);
  temp `.part` + atomic rename; never trust filename or `Content-Type` as APK proof.
- Network/parse workloads bounded (timeouts, body caps, page caps).

## 3. Artifact verification (honest states)

`VerificationResult`: CHECKED_OK (digest from trustworthy source + match) /
UNAVAILABLE (API `digest: null`, the common case) / MISMATCH (block install
prompt, quarantine file) / UNTRUSTED_SOURCE (digest published alongside file by
same source — displayed, never a safety claim). APK structural check (ZIP +
manifest) is format validation, not authenticity. Signing-cert inspection, if
added, reports exactly what matched and never equals authorship trust.

## 4. Install & device

No silent install; system installer is the only executor; downloaded files never
executed in-process. No broad package visibility (no QUERY_ALL_PACKAGES);
installed-state uses targeted queries only. Every artifact screen shows origin
repo + release + checksum state + a "report suspicious entry" action (local
blocklist + optional maintainer report link).

## 5. Play-policy note

`REQUEST_INSTALL_PACKAGES` on Play-distributed builds faces policy scrutiny;
plan: Play build may disable direct install handoff (export-via-picker only)
vs full sideload/F-Droid build. Decided at M6; recorded in DECISIONS.md.
