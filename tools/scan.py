#!/usr/bin/env python3
"""Daily GitHub catalog scanner for G Store.

Searches GitHub for repositories that publish APK release artifacts and
stores the result as JSON under catalog/. The Android app fetches
catalog/apps.json from raw.githubusercontent.com on launch, so the Home
tab always has a list even before any on-device sync.

Coverage limits (honest by design, see docs/GITHUB_DISCOVERY.md):
- GitHub has NO endpoint enumerating every repo with APK releases. This
  scanner rotates 10 query sets (one per day) across languages, game
  engines, app domains, and star tiers, and MERGES each run into the
  previous catalog instead of replacing it — coverage accumulates over the
  10-day cycle toward the whole APK-bearing surface without ever claiming
  to be exhaustive.
- Budget: the automatic GITHUB_TOKEN allows ~5,000 REST requests/hour;
  search calls + one releases call per candidate must fit comfortably, so
  MAX_CHECK caps candidates per run (default 8000, ~1,600 req/hr over a
  5-hour run). A 300-minute soft deadline (SCAN_DEADLINE_S) stops starting
  new repos in time for the 360-min job timeout; partial results are
  always written and committed, flagged in meta.json.
- Entries carry last_verified (ISO date). Entries unseen for STALE_DAYS
  (default 30) are dropped; a repo re-checked with no APK release is
  dropped immediately. Archived/disabled repos and drafts are excluded.
  Digest is usually null (GitHub rarely provides one).

Stdlib only (urllib) so ubuntu-latest runners need no setup.
Env: GITHUB_TOKEN (required), MAX_CHECK (default 850), OUT_DIR (default catalog).
"""
import json
import os
import sys
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timezone

BASE = "https://api.github.com"
TOKEN = os.environ.get("GITHUB_TOKEN", "")
MAX_CHECK = int(os.environ.get("MAX_CHECK", "8000"))
OUT_DIR = os.environ.get("OUT_DIR", "catalog")
# Soft deadline: the workflow kills the job at 360 min, which would discard
# everything if files were only written at the end. Stop starting new repos
# after DEADLINE_S and commit whatever we have (meta flags it as partial).
DEADLINE_S = int(os.environ.get("SCAN_DEADLINE_S", str(300 * 60)))
PER_PAGE = 100
SEARCH_PAGES = 10  # GitHub caps search pagination at 1,000 results
RELEASES_PER_REPO = 10
ENTRY_CAP = 8000
NOTES_CAP = 2000
ASSETS_CAP = 20
# Entries not re-verified within this window are dropped as stale.
STALE_DAYS = int(os.environ.get("SCAN_STALE_DAYS", "30"))

# Ten query sets — one runs per day (day-of-year % 10). Every query carries
# archived:false so dead repos never consume check budget. Major languages
# are split into star tiers (10..100 / 100..1000 / >1000) because search
# pagination caps at 1,000 results per query — one query can never cover a
# whole language. Mobile stacks covered: Kotlin, Java, Dart/Flutter,
# C#/Xamarin-MAUI, JavaScript/TypeScript, Python (Kivy/BeeWare/Chaquopy),
# C++ NDK, Rust, Go, Scala, Godot, Unity, LÖVE.
QUERY_SETS = [
    [  # Day 0 — Android headliners
        "topic:android archived:false fork:false",
        "android language:Kotlin archived:false fork:false stars:>1000",
        "android language:Java archived:false fork:false stars:>1000",
    ],
    [  # Day 1 — Kotlin/Java long tail (tier splits beat the 1k cap)
        "android language:Kotlin archived:false fork:false stars:10..100",
        "android language:Kotlin archived:false fork:false stars:100..1000",
        "android language:Java archived:false fork:false stars:10..100",
        "android language:Java archived:false fork:false stars:100..1000",
    ],
    [  # Day 2 — cross-platform stacks
        "topic:flutter archived:false fork:false",
        "topic:react-native archived:false fork:false",
        "android language:Dart archived:false fork:false stars:>50",
        "android language:C# archived:false fork:false stars:>50",
    ],
    [  # Day 3 — media domain
        "android music player archived:false fork:false",
        "android video player archived:false fork:false",
        "youtube android archived:false fork:false",
        "android camera archived:false fork:false",
    ],
    [  # Day 4 — system domain
        "android file manager archived:false fork:false",
        "android launcher archived:false fork:false",
        "android keyboard archived:false fork:false",
        "android vpn archived:false fork:false",
    ],
    [  # Day 5 — productivity domain
        "android notes todo archived:false fork:false",
        "android rss reader archived:false fork:false",
        "android weather archived:false fork:false",
        "android password manager archived:false fork:false",
    ],
    [  # Day 6 — free-software ecosystem
        "topic:f-droid archived:false fork:false",
        "android adblock archived:false fork:false",
        "android firewall archived:false fork:false",
        "android backup archived:false fork:false",
    ],
    [  # Day 7 — game engines and emulators
        "godot android archived:false fork:false",
        "unity android archived:false fork:false",
        "android game archived:false fork:false",
        "android emulator archived:false fork:false",
    ],
    [  # Day 8 — Python-mobile and native stacks
        "kivy android archived:false fork:false",
        "chaquopy android archived:false fork:false",
        "android language:Python archived:false fork:false",
        "android language:C++ archived:false fork:false",
    ],
    [  # Day 9 — comms, maps, and everyday domains
        "android messenger archived:false fork:false",
        "android maps navigation archived:false fork:false",
        "android finance archived:false fork:false",
        "android fitness archived:false fork:false",
    ],
]

HEADERS = {
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "gstore-catalog-scanner",
}
if TOKEN:
    HEADERS["Authorization"] = "Bearer " + TOKEN


def api_get(path):
    """GET with retries for 5xx/429/403-rate-limit; returns (status, json, headers)."""
    url = BASE + path
    for attempt in range(4):
        req = urllib.request.Request(url, headers=HEADERS)
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                remaining = int(resp.headers.get("X-RateLimit-Remaining", "9999"))
                reset = int(resp.headers.get("X-RateLimit-Reset", "0"))
                if remaining < 60:
                    wait = max(0, reset - int(time.time()) + 5)
                    if wait > 0:
                        print(f"rate low ({remaining}), sleeping {wait}s", flush=True)
                        time.sleep(wait)
                return resp.status, json.load(resp), resp.headers
        except urllib.error.HTTPError as e:
            if e.code in (403, 429):
                retry = e.headers.get("Retry-After")
                reset = int(e.headers.get("X-RateLimit-Reset", "0") or 0)
                wait = int(retry) if retry else max(0, reset - int(time.time()) + 5)
                wait = min(max(wait, 30), 600)
                print(f"rate limited ({e.code}), sleeping {wait}s", flush=True)
                time.sleep(wait)
                continue
            if e.code in (404, 451):
                return e.code, None, e.headers
            if 500 <= e.code < 600 and attempt < 3:
                time.sleep(2 ** attempt)
                continue
            raise
    raise RuntimeError(f"GET {path} failed after retries")


def search_candidates(queries):
    seen = {}
    for q in queries:
        for page in range(1, SEARCH_PAGES + 1):
            params = urllib.parse.urlencode(
                {"q": q, "sort": "stars", "order": "desc",
                 "per_page": PER_PAGE, "page": page}
            )
            status, data, _ = api_get("/search/repositories?" + params)
            if status != 200 or not data:
                break
            items = data.get("items", [])
            for repo in items:
                if repo.get("archived") or repo.get("disabled"):
                    continue
                seen.setdefault(repo["id"], repo)
            print(f"query {q!r} page {page}: {len(items)} items, {len(seen)} unique",
                  flush=True)
            if len(items) < PER_PAGE:
                break
            time.sleep(2.5)  # secondary rate-limit courtesy (search: 30/min)
    ranked = sorted(seen.values(), key=lambda r: r.get("stargazers_count", 0),
                    reverse=True)
    return ranked[:MAX_CHECK]


def pick_apk_release(full_name):
    status, releases, _ = api_get(
        f"/repos/{full_name}/releases?per_page={RELEASES_PER_REPO}")
    if status != 200 or not releases:
        return None, []
    stable = [r for r in releases if not r.get("prerelease")]
    for rel in stable + releases:  # stable first, prerelease fallback (flagged)
        apks = [a for a in rel.get("assets", [])
                if a.get("name", "").lower().endswith(".apk")]
        if apks:
            return rel, rel.get("assets", [])[:ASSETS_CAP]
    return None, []


def to_entry(repo, rel, assets, today):
    lic = repo.get("license") or {}
    spdx = lic.get("spdx_id")
    return {
        "schema": 1,
        "last_verified": today,
        "repo": {
            "id": repo["id"],
            "full_name": repo["full_name"],
            "owner": repo["owner"]["login"],
            "avatar_url": repo["owner"].get("avatar_url"),
            "description": repo.get("description"),
            "stars": repo.get("stargazers_count", 0),
            "forks": repo.get("forks_count", 0),
            "topics": repo.get("topics", [])[:20],
            "archived": False,
            "default_branch": repo.get("default_branch", "main"),
            "updated_at": repo.get("updated_at"),
            "created_at": repo.get("created_at"),
            "license": (spdx if spdx and spdx != "NOASSERTION"
                        else lic.get("name")),
        },
        "release": {
            "id": rel["id"],
            "tag": rel["tag_name"],
            "name": rel.get("name"),
            "notes": (rel.get("body") or "")[:NOTES_CAP],
            "published_at": rel.get("published_at"),
            "html_url": rel.get("html_url"),
            "prerelease": bool(rel.get("prerelease")),
        },
        "assets": [{
            "id": a["id"],
            "name": a["name"],
            "content_type": a.get("content_type"),
            "size": a.get("size", 0),
            "download_url": a.get("browser_download_url"),
            "digest": a.get("digest"),
            "download_count": a.get("download_count", 0),
        } for a in assets],
    }


def load_previous():
    """Previous catalog keyed by repo id; tolerant of a missing first run."""
    path = os.path.join(OUT_DIR, "apps.json")
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError):
        return {}
    return {e["repo"]["id"]: e for e in data if e.get("repo", {}).get("id")}


def main():
    if not TOKEN:
        print("GITHUB_TOKEN is empty: 60 req/hr cannot cover a scan. Abort.",
              file=sys.stderr)
        return 2
    started = time.time()
    today = date.today().isoformat()
    set_index = date.today().toordinal() % len(QUERY_SETS)
    queries = QUERY_SETS[set_index]
    print(f"query set {set_index}: {queries}", flush=True)
    error = None
    deadline_hit = False
    previous = load_previous()
    # Grandfather entries written before last_verified existed.
    for entry in previous.values():
        entry.setdefault("last_verified", today)
    carried = len(previous)
    # The check phase must never kill the write: ANY unexpected failure
    # (search fatal after retries, runner network blip) is recorded and we
    # still commit the merged catalog with an error flag. A run that writes
    # nothing AND fails is indistinguishable from success downstream.
    candidates = []
    checked = 0
    verified_today = 0
    checked_names = set()
    try:
        candidates = search_candidates(queries)
        for repo in candidates:
            if time.time() - started > DEADLINE_S:
                deadline_hit = True
                print(f"deadline ({DEADLINE_S}s) hit after {checked} repos; "
                      f"finalizing partial results", flush=True)
                break
            checked += 1
            checked_names.add(repo["full_name"])
            try:
                rel, assets = pick_apk_release(repo["full_name"])
            except Exception as e:  # one bad repo must not kill the run
                print(f"skip {repo['full_name']}: {e}", flush=True)
                continue
            if rel is not None:
                previous[repo["id"]] = to_entry(repo, rel, assets, today)
                verified_today += 1
            elif repo["id"] in previous:
                # Re-checked and no longer ships an APK release — drop it.
                del previous[repo["id"]]
            time.sleep(0.3)
    except Exception:
        error = traceback.format_exc(limit=5)
        print(f"check phase failed, committing merged catalog: {error}",
              flush=True)
    # Drop entries unseen for STALE_DAYS (repos deleted, renamed, or rotated
    # out of every query surface).
    dropped_stale = 0
    for repo_id, entry in list(previous.items()):
        try:
            age = (date.fromisoformat(today) -
                   date.fromisoformat(entry.get("last_verified", today))).days
        except ValueError:
            age = 0
        if age > STALE_DAYS:
            del previous[repo_id]
            dropped_stale += 1
    entries = sorted(previous.values(),
                     key=lambda e: e["repo"].get("stars", 0), reverse=True)
    entries = entries[:ENTRY_CAP]
    try:
        write_results(entries, candidates, checked, started, deadline_hit,
                      error, set_index, queries, verified_today, carried,
                      dropped_stale)
    except Exception:
        error = traceback.format_exc(limit=3)
        print(f"write failed: {error}", flush=True)
        return 1
    print(f"done: {checked} checked, {verified_today} verified today, "
          f"{len(entries)} total entries", flush=True)
    return 0


def write_results(entries, candidates, checked, started,
                  deadline_hit, error, set_index, queries,
                  verified_today, carried, dropped_stale):
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, "apps.json"), "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)
    meta = {
        "schema": 1,
        "scanned_at": datetime.now(timezone.utc).isoformat(),
        "tool": "tools/scan.py",
        "query_set": set_index,
        "queries": queries,
        "candidates": len(candidates),
        "checked": checked,
        "verified_today": verified_today,
        "carried_over": carried,
        "dropped_stale": dropped_stale,
        "matches": len(entries),
        "truncated": len(entries) >= ENTRY_CAP or deadline_hit,
        "deadline_hit": deadline_hit,
        "complete": error is None and not deadline_hit,
        "error": error,
        "duration_s": round(time.time() - started, 1),
    }
    with open(os.path.join(OUT_DIR, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    sys.exit(main())
