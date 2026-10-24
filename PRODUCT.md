# Forkly — Product Context (impeccable)

Source of truth for behavior: `docs/PRODUCT_SPEC.md`. This file exists for the
impeccable design skill: audience, mode, and visual authority pointer.

## What it is

Forkly is a native Android (Kotlin + Compose + Material 3) marketplace that
discovers, lists, and helps users install APK artifacts published through
GitHub Releases. No backend: a scheduled GitHub Action scans GitHub daily and
commits `catalog/apps.json` to this repo; the app pulls it on launch.

## Who it's for

Android users who sideload open-source apps from GitHub and want one
trustworthy shelf: what's new, what's trending, what's proven — with honest
APK/ABI/checksum state before they download.

## Design mode

**Operate.** The visitor completes a task: find an app, judge its
trustworthiness, download the right artifact. Scanability, consistency, and
native Material expectations outrank expression. Brand lives in precise
details: tonal chips, exact counts, honest badges.

## Visual authority

`DESIGN.md` (repo root) defines the committed world. `docs/UI_UX_SPEC.md`
holds the full interaction/accessibility contract. When they conflict on
visuals, `DESIGN.md` wins; on behavior, `UI_UX_SPEC.md` wins.

## Assumptions (labeled per skill rule)

- No PRODUCT.md existed; this file infers context from the explicit brief
  (`AGENTS.md` + user requests). Nothing here overrides `docs/*`.
- No emulator/device is attached to this workstation, so visual verification
  is by build + lint + the impeccable detector, not screenshots.
