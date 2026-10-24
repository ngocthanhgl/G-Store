#!/usr/bin/env python3
"""Scheduled GitHub catalog scanner for Forkly.

Searches GitHub for repositories that publish APK release artifacts and
stores the result as JSON under catalog/. The Android app fetches
catalog/apps.json from raw.githubusercontent.com on launch, so the Home
tab always has a list even before any on-device sync.

Coverage limits (honest by design, see docs/GITHUB_DISCOVERY.md):
- GitHub has NO endpoint enumerating every repo with APK releases. This
scanner rotates 14 query sets across languages, game engines, app
domains, and star tiers, and MERGES each run into the previous catalog
instead of replacing it — coverage accumulates over the rotating query
surface without ever claiming to be exhaustive.
- Runs twice per day. GitHub kills a hosted job at 360 minutes, so the
workflow runs the scanner as resumable chunks (SCAN_DEADLINE_S per chunk)
and commits after every chunk: a killed job can lose at most one chunk.
Progress lives in catalog/scan_state.json (query sets + checked repo ids),
so the next chunk — or the next job twelve hours later — continues the same
sets instead of starting over. DEADLINE_S=0 disables the limit entirely
(scan until every candidate is checked).
- Budget: the automatic GITHUB_TOKEN allows ~5,000 REST requests/hour.
Checks are paced at SCAN_REQUEST_INTERVAL_S (~1 req/s => ~3,000/hr) so a
run never exhausts the hourly quota, and MAX_CHECK caps candidates per run
(default 12000) to stay inside a day's job budget.
- Entries carry last_verified (ISO date). Entries unseen for STALE_DAYS
  (default 30) are dropped; a repo re-checked with no APK release is
  dropped immediately. Archived/disabled repos and drafts are excluded.
  Digest is usually null (GitHub rarely provides one).

Stdlib only (urllib) so ubuntu-latest runners need no setup.
Env: GITHUB_TOKEN (required), MAX_CHECK (default 12000), OUT_DIR (default catalog),
SCAN_DEADLINE_S (default 0 = unlimited), SCAN_REQUEST_INTERVAL_S (default 0.9).
"""
import http.client
import json
import os
import signal
import socket
import sys
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timezone
BASE = "https://api.github.com"
TOKEN = os.environ.get("GITHUB_TOKEN", "")
MAX_CHECK = int(os.environ.get("MAX_CHECK", "12000"))
OUT_DIR = os.environ.get("OUT_DIR", "catalog")
# 0 (default) = unlimited: scan until every candidate for this query slot is
# checked. The workflow sets a per-chunk value so a 360-min job cap can never
# kill work that was not already committed as a checkpoint.
DEADLINE_S = int(os.environ.get("SCAN_DEADLINE_S", "0"))
# Pace release checks at ~1 req/s => ~3,000 calls/hour, under the 5,000/hr
# authenticated quota with headroom for search pages, retries, and the next
# chunk. Lower it to scan faster, raise it to be gentler on the quota.
REQUEST_INTERVAL_S = float(os.environ.get("SCAN_REQUEST_INTERVAL_S", "0.9"))
# Search pages carry full repo objects (~7 KB each): a 100-item page is a
# ~1.4 MB body that truncates mid-stream (IncompleteRead) often enough to
# survive all 4 transport retries and kill the whole search phase (seen
# 2026-10-16, query_set 5: 0 candidates, catalog frozen at carry-over).
# Halve the page size and double the page count for the same 1,000-result
# cap with bodies small enough to survive.
SEARCH_PER_PAGE = 50
SEARCH_PAGES = 20  # GitHub caps search pagination at 1,000 results
RELEASES_PER_REPO = 10
ENTRY_CAP = 12000
NOTES_CAP = 2000
ASSETS_CAP = 20
# Slim app index: top entries by stars, repo metadata + latest APK release
# pointer only (no notes, no per-asset rows). The app pulls this on launch;
# full asset lists load lazily per repo via the GitHub API on demand.
INDEX_CAP = 3500
DESC_CAP = 240
# Entries not re-verified within this window are dropped as stale.
STALE_DAYS = int(os.environ.get("SCAN_STALE_DAYS", "30"))
# Resume point: query sets + ids already checked, so a killed job (or the next
# scheduled run) continues instead of restarting.
SCAN_STATE_FILE = "scan_state.json"
QUEUE_FILE = "scan_queue.json"

# Fourteen query sets — two adjacent sets run per scheduled scan (twice a
# day), advancing two sets each slot so the full query surface rotates in
# seven scans (3.5 days).
# Every query carries archived:false so dead repos never consume check budget.
# Major languages
# are split into star tiers (10..100 / 100..1000 / >1000) because search
# pagination caps at 1,000 results per query — one query can never cover a
# whole language. Mobile stacks covered: Kotlin, Java, Dart/Flutter,
# C#/Xamarin-MAUI, JavaScript/TypeScript, Python (Kivy/BeeWare/Chaquopy),
# C++ NDK, Rust, Go, Scala, Godot, Unity, LÖVE, APK topics, form factors
# (TV/Wear), and the low-star tail.
QUERY_SETS = [
    [  # Set 0 — Android headliners
        "topic:android archived:false fork:false",
        "android language:Kotlin archived:false fork:false stars:>1000",
        "android language:Java archived:false fork:false stars:>1000",
    ],
    [  # Set 1 — Kotlin/Java long tail (tier splits beat the 1k cap)
        "android language:Kotlin archived:false fork:false stars:10..100",
        "android language:Kotlin archived:false fork:false stars:100..1000",
        "android language:Java archived:false fork:false stars:10..100",
        "android language:Java archived:false fork:false stars:100..1000",
    ],
    [  # Set 2 — cross-platform stacks
        "topic:flutter archived:false fork:false",
        "topic:react-native archived:false fork:false",
        "android language:Dart archived:false fork:false stars:>50",
        "android language:C# archived:false fork:false stars:>50",
    ],
    [  # Set 3 — media domain
        "android music player archived:false fork:false",
        "android video player archived:false fork:false",
        "youtube android archived:false fork:false",
        "android camera archived:false fork:false",
        "android podcast archived:false fork:false",
        "android manga comic archived:false fork:false",
    ],
    [  # Set 4 — system domain
        "android file manager archived:false fork:false",
        "android launcher archived:false fork:false",
        "android keyboard archived:false fork:false",
        "android vpn archived:false fork:false",
        "android accessibility archived:false fork:false",
        "android home automation archived:false fork:false",
    ],
    [  # Set 5 — productivity domain
        "android notes todo archived:false fork:false",
        "android rss reader archived:false fork:false",
        "android weather archived:false fork:false",
        "android password manager archived:false fork:false",
        "android calendar archived:false fork:false",
        "android email archived:false fork:false",
        "android education archived:false fork:false",
    ],
    [  # Set 6 — free-software ecosystem
        "topic:f-droid archived:false fork:false",
        "android adblock archived:false fork:false",
        "android firewall archived:false fork:false",
        "android backup archived:false fork:false",
        "android privacy archived:false fork:false",
        "android encryption archived:false fork:false",
        "android 2fa archived:false fork:false",
    ],
    [  # Set 7 — game engines and emulators
        "godot android archived:false fork:false",
        "unity android archived:false fork:false",
        "android game archived:false fork:false",
        "android emulator archived:false fork:false",
        "android libgdx archived:false fork:false",
        "android bevy archived:false fork:false",
        "android renpy archived:false fork:false",
    ],
    [  # Set 8 — Python-mobile and native stacks
        "kivy android archived:false fork:false",
        "chaquopy android archived:false fork:false",
        "android language:Python archived:false fork:false",
        "android language:C++ archived:false fork:false",
        "android language:JavaScript archived:false fork:false",
    ],
    [  # Set 9 — comms, maps, and everyday domains
        "android messenger archived:false fork:false",
        "android maps navigation archived:false fork:false",
        "android finance archived:false fork:false",
        "android fitness archived:false fork:false",
        "android travel archived:false fork:false",
        "android transit archived:false fork:false",
        "android recipes archived:false fork:false",
    ],
    [  # Set 10 — compiled-native stacks
        "android language:Rust archived:false fork:false",
        "android language:Go archived:false fork:false",
        "android language:Scala archived:false fork:false",
        "android language:C archived:false fork:false",
        "android git client archived:false fork:false",
        "android terminal archived:false fork:false",
        "android code editor archived:false fork:false",
    ],
    [  # Set 11 — APK topics and form factors
        "topic:apk archived:false fork:false",
        "topic:android-app archived:false fork:false",
        "android tv archived:false fork:false",
        "android wear os archived:false fork:false",
        "android wallpaper archived:false fork:false",
        "android icon pack archived:false fork:false",
    ],
    [  # Set 12 — low-star long tail (pagination still caps at 1k/query)
        "android language:Kotlin archived:false fork:false stars:<10",
        "android language:Java archived:false fork:false stars:<10",
        "android language:Dart archived:false fork:false stars:<10",
        "android language:C++ archived:false fork:false stars:<10",
        "android language:Rust archived:false fork:false stars:<10",
    ],
    [  # Set 13 — more everyday domains
        "android podcast archived:false fork:false",
        "android ebook reader archived:false fork:false",
        "android expense tracker archived:false fork:false",
        "android habit tracker archived:false fork:false",
        "android translation archived:false fork:false",
        "android shopping archived:false fork:false",
    ],
]
QUERY_SETS_PER_RUN = 2
# Two scheduled runs per day (cron 02:00 + 14:00 UTC). Each run advances two
# sets, so all 14 sets rotate every seven scans (3.5 days).
SCHEDULE_INTERVAL_SECONDS = 12 * 60 * 60

HEADERS = {
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "forkly-catalog-scanner",
}
if TOKEN:
    HEADERS["Authorization"] = "Bearer " + TOKEN

# Set by SIGTERM/SIGINT so a cancelled job still checkpoints instead of
# dropping everything scanned since the previous commit.
STOP = {"requested": False}


def request_stop(signum, _frame):
    if not STOP["requested"]:
        print(f"signal {signum}: finishing the current repo, then checkpointing",
              flush=True)
    STOP["requested"] = True


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
                # Read the body fully BEFORE parsing: a dropped connection
                # mid-stream raises IncompleteRead, which must retry like a
                # 5xx instead of killing the whole run (seen 2026-10-12 on a
                # ~574 KB search page).
                raw = resp.read()
                return resp.status, json.loads(raw.decode("utf-8")), resp.headers
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
        except (http.client.IncompleteRead, http.client.RemoteDisconnected,
                ConnectionError, TimeoutError, socket.timeout) as e:
            # Transient transport failure — same backoff as a 5xx.
            if attempt < 3:
                wait = 2 ** attempt
                print(f"transport error ({type(e).__name__}), retry in {wait}s",
                      flush=True)
                time.sleep(wait)
                continue
            raise
    raise RuntimeError(f"GET {path} failed after retries")


def query_set_indices(now=None):
    """Pick two rotating query sets for this scheduled twelve-hour slot."""
    now = now or datetime.now(timezone.utc)
    slot = int(now.timestamp() // SCHEDULE_INTERVAL_SECONDS)
    start = (slot * QUERY_SETS_PER_RUN) % len(QUERY_SETS)
    return [(start + offset) % len(QUERY_SETS)
            for offset in range(QUERY_SETS_PER_RUN)]


def plan_run(state, now=None):
    """Decide what this process should do: resume, skip, or start fresh.

    An unfinished state always resumes, even in a later slot, so a query set
    is never abandoned half-checked. A finished state for the current slot
    means extra chunk steps in the same job have nothing left to do.
    """
    now = now or datetime.now(timezone.utc)
    slot = int(now.timestamp() // SCHEDULE_INTERVAL_SECONDS)
    if state and state.get("complete") is False and state.get("queries"):
        return {
            "mode": "resume",
            "slot": state.get("slot", slot),
            "query_sets": state.get("query_sets") or [],
            "queries": state["queries"],
            "checked_ids": [int(x) for x in (state.get("checked_ids") or [])],
            "started_at": state.get("started_at") or now.isoformat(),
        }
    if state and state.get("complete") and state.get("slot") == slot:
        return {
            "mode": "done",
            "slot": slot,
            "query_sets": state.get("query_sets") or [],
            "queries": state.get("queries") or [],
            "checked_ids": [],
            "started_at": state.get("started_at") or now.isoformat(),
        }
    set_indices = query_set_indices(now)
    return {
        "mode": "fresh",
        "slot": slot,
        "query_sets": set_indices,
        "queries": [q for index in set_indices for q in QUERY_SETS[index]],
        "checked_ids": [],
        "started_at": now.isoformat(),
    }


def load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def write_json(path, payload):
    """Atomic write: a killed process never leaves a half-written catalog."""
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
    os.replace(tmp, path)


def queue_path():
    return os.path.join(OUT_DIR, QUEUE_FILE)


def load_queue(queries):
    """Candidate repo objects cached for this job; empty when absent/stale."""
    data = load_json(queue_path())
    if not data or data.get("queries") != queries:
        return []
    return data.get("candidates") or []


def save_queue(candidates, queries):
    try:
        os.makedirs(OUT_DIR, exist_ok=True)
        write_json(queue_path(), {"queries": queries,
                                  "candidates": candidates})
    except OSError as e:
        print(f"queue cache not written: {e}", flush=True)


def clear_queue():
    try:
        os.remove(queue_path())
    except OSError:
        pass


def build_entries(previous, today):
    """Drop entries unseen for STALE_DAYS and return the capped, ranked list."""
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
    return entries[:ENTRY_CAP], dropped_stale



def interleave_candidates(groups, limit):
    """Round-robin query groups so broad searches cannot crowd out others."""
    positions = [0] * len(groups)
    seen = set()
    result = []
    while len(result) < limit:
        added = False
        for index, group in enumerate(groups):
            while positions[index] < len(group):
                repo = group[positions[index]]
                positions[index] += 1
                repo_id = repo.get("id")
                if repo_id in seen:
                    continue
                seen.add(repo_id)
                result.append(repo)
                added = True
                break
            if len(result) >= limit:
                break
        if not added:
            break
    return result


def search_candidates(queries):
    seen = {}
    groups = []
    for q in queries:
        query_seen = {}
        try:
            for page in range(1, SEARCH_PAGES + 1):
                params = urllib.parse.urlencode(
                    {"q": q, "sort": "stars", "order": "desc",
                     "per_page": SEARCH_PER_PAGE, "page": page}
                )
                status, data, _ = api_get("/search/repositories?" + params)
                if status != 200 or not data:
                    break
                items = data.get("items", [])
                for repo in items:
                    if repo.get("archived") or repo.get("disabled"):
                        continue
                    seen.setdefault(repo["id"], repo)
                    query_seen.setdefault(repo["id"], repo)
                print(f"query {q!r} page {page}: {len(items)} items, {len(seen)} unique",
                      flush=True)
                if len(items) < SEARCH_PER_PAGE:
                    break
                time.sleep(2.5)  # secondary rate-limit courtesy (search: 30/min)
        except Exception as e:
            # One poisoned query (a page that truncates through every
            # retry) must not nuke the other queries: skip it and keep
            # whatever candidates the healthy queries returned.
            print(f"query {q!r} aborted ({type(e).__name__}: {e}), "
                  f"continuing with {len(seen)} candidates", flush=True)
        ranked = sorted(query_seen.values(),
                        key=lambda r: r.get("stargazers_count", 0),
                        reverse=True)
        groups.append(ranked)
    return interleave_candidates(groups, MAX_CHECK)


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
            "language": repo.get("language"),
            "openIssues": repo.get("open_issues_count"),
            "watchers": repo.get("subscribers_count"),
            "homepage": repo.get("homepage"),
            "pushedAt": repo.get("pushed_at"),
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
    for signame in ("SIGTERM", "SIGINT"):
        handler = getattr(signal, signame, None)
        if handler is None:
            continue
        try:
            signal.signal(handler, request_stop)
        except (OSError, ValueError):
            pass
    started = time.time()
    now = datetime.now(timezone.utc)
    today = now.date().isoformat()
    plan = plan_run(load_json(os.path.join(OUT_DIR, SCAN_STATE_FILE)), now)
    if plan["mode"] == "done":
        print(f"query sets {plan['query_sets']} already fully scanned for "
              f"slot {plan['slot']}; nothing to do", flush=True)
        return 0
    resumed = plan["mode"] == "resume"
    set_indices = plan["query_sets"]
    queries = plan["queries"]
    slot = plan["slot"]
    started_at = plan["started_at"]
    checked_ids = set(plan["checked_ids"])
    print(f"query sets {set_indices} ({plan['mode']}): {queries}", flush=True)
    previous = load_previous()
    # Grandfather entries written before last_verified existed.
    for entry in previous.values():
        entry.setdefault("last_verified", today)
    carried = len(previous)
    error = None
    finished = False
    stopped = False
    checked = 0
    verified_today = 0

    def flush(complete):
        entries, dropped_stale = build_entries(previous, today)
        meta = {
            "query_set": set_indices[0] if set_indices else None,
            "query_sets": set_indices,
            "queries": queries,
            "candidates": len(candidates),
            "pending": len(pending),
            "checked": checked,
            "checked_total": len(checked_ids),
            "verified_today": verified_today,
            "carried_over": carried,
            "dropped_stale": dropped_stale,
            "truncated": len(entries) >= ENTRY_CAP or not complete,
            "resumed": resumed,
            "interrupted": stopped,
            "complete": complete,
            "error": error,
            "duration_s": round(time.time() - started, 1),
        }
        state = {
            "schema": 1,
            "slot": slot,
            "query_sets": set_indices,
            "queries": queries,
            # A complete slot needs no resume point: drop the id list so the
            # committed state stays tiny.
            "checked_ids": [] if complete else sorted(checked_ids),
            "candidates": len(candidates),
            "started_at": started_at,
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "complete": complete,
        }
        write_results(entries, meta, state)

    candidates = []
    pending = []
    # The check phase must never kill the write: ANY unexpected failure
    # (search fatal after retries, runner network blip) is recorded and we
    # still commit the merged catalog with an error flag. A run that writes
    # nothing AND fails is indistinguishable from success downstream.
    try:
        # A previous chunk in the same job cached the candidate objects;
        # reuse them instead of paying for the whole search phase again.
        candidates = load_queue(queries) if resumed else []
        if not candidates:
            candidates = search_candidates(queries)
            save_queue(candidates, queries)
        pending = [repo for repo in candidates
                   if repo.get("id") not in checked_ids]
        print(f"{len(candidates)} candidates, {len(pending)} pending, "
              f"{len(checked_ids)} already checked", flush=True)
        for repo in pending:
            if STOP["requested"]:
                stopped = True
                print(f"stop requested after {checked} repos; checkpointing",
                      flush=True)
                break
            if DEADLINE_S and time.time() - started > DEADLINE_S:
                print(f"chunk deadline ({DEADLINE_S}s) after {checked} repos; "
                      f"checkpointing for the next chunk", flush=True)
                break
            checked += 1
            checked_ids.add(repo["id"])
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
            time.sleep(REQUEST_INTERVAL_S)
        else:
            finished = True
    except Exception:
        error = traceback.format_exc(limit=5)
        print(f"check phase failed, committing merged catalog: {error}",
              flush=True)
    complete = finished and error is None
    try:
        flush(complete)
    except Exception:
        error = traceback.format_exc(limit=3)
        print(f"write failed: {error}", flush=True)
        return 1
    if complete:
        clear_queue()
    print(f"done: {checked} checked this chunk, {len(checked_ids)} total, "
          f"{verified_today} verified today, "
          f"{'complete' if complete else 'resumable'}", flush=True)
    return 0


def to_index_entry(entry):
    """Slim app-facing projection: metadata + latest-APK-release pointer."""
    repo = entry.get("repo", {})
    rel = entry.get("release", {}) or {}
    desc = repo.get("description") or ""
    return {
        "schema": 1,
        "last_verified": entry.get("last_verified"),
        "repo": {
            "id": repo.get("id"),
            "full_name": repo.get("full_name"),
            "owner": repo.get("owner"),
            "avatar_url": repo.get("avatar_url"),
            "description": desc[:DESC_CAP],
            "stars": repo.get("stars", 0),
            "forks": repo.get("forks", 0),
            "topics": (repo.get("topics") or [])[:20],
            "archived": bool(repo.get("archived", False)),
            "default_branch": repo.get("default_branch", "main"),
            "updated_at": repo.get("updated_at"),
            "created_at": repo.get("created_at"),
            "license": repo.get("license"),
        },
        "release": {
            "id": rel.get("id"),
            "tag": rel.get("tag"),
            "published_at": rel.get("published_at"),
            "prerelease": bool(rel.get("prerelease", False)),
            "apk_count": len(entry.get("assets") or []),
        },
    }


def write_results(entries, meta, state):
    """Commit one checkpoint: catalog files, run meta, and resume state."""
    os.makedirs(OUT_DIR, exist_ok=True)
    write_json(os.path.join(OUT_DIR, "apps.json"), entries)
    index = [to_index_entry(e) for e in entries[:INDEX_CAP]]
    write_json(os.path.join(OUT_DIR, "index.json"), index)
    payload = {
        "schema": 1,
        "scanned_at": datetime.now(timezone.utc).isoformat(),
        "tool": "tools/scan.py",
    }
    payload.update(meta)
    payload["matches"] = len(entries)
    payload["index_matches"] = len(index)
    payload["index_bytes"] = os.path.getsize(os.path.join(OUT_DIR, "index.json"))
    write_json(os.path.join(OUT_DIR, "meta.json"), payload)
    write_json(os.path.join(OUT_DIR, SCAN_STATE_FILE), state)


if __name__ == "__main__":
    sys.exit(main())
