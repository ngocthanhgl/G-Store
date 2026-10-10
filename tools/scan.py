#!/usr/bin/env python3
"""Daily GitHub catalog scanner for G Store.

Searches GitHub for repositories that publish APK release artifacts and
stores the result as JSON under catalog/. The Android app fetches
catalog/apps.json from raw.githubusercontent.com on launch, so the Home
tab always has a list even before any on-device sync.

Coverage limits (honest by design, see docs/GITHUB_DISCOVERY.md):
- GitHub has NO endpoint enumerating every repo with APK releases. This
  scanner combines a few high-recall repository-search queries, inspects
  recent releases per candidate, and records matches. Coverage is a
  documented sample, never "all of GitHub".
- Budget: the automatic GITHUB_TOKEN allows ~1,000 REST requests/hour.
  Search calls + one releases call per candidate must fit that budget,
  so MAX_CHECK caps candidates per run (default 850).
- Archived/disabled repos and drafts are excluded. Digest is usually
  null (GitHub rarely provides one) — the app treats it as UNAVAILABLE.

Stdlib only (urllib) so ubuntu-latest runners need no setup.
Env: GITHUB_TOKEN (required), MAX_CHECK (default 850), OUT_DIR (default catalog).
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone

BASE = "https://api.github.com"
TOKEN = os.environ.get("GITHUB_TOKEN", "")
MAX_CHECK = int(os.environ.get("MAX_CHECK", "850"))
OUT_DIR = os.environ.get("OUT_DIR", "catalog")
PER_PAGE = 100
RELEASES_PER_REPO = 5
ENTRY_CAP = 1000
NOTES_CAP = 2000
ASSETS_CAP = 20

QUERIES = [
    "topic:android fork:false",
    "android language:Kotlin fork:false stars:>100",
    "android language:Java fork:false stars:>100",
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


def search_candidates():
    seen = {}
    for q in QUERIES:
        for page in range(1, 6):  # GitHub caps search pagination at 1,000 results
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


def to_entry(repo, rel, assets):
    lic = repo.get("license") or {}
    spdx = lic.get("spdx_id")
    return {
        "schema": 1,
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


def main():
    if not TOKEN:
        print("GITHUB_TOKEN is empty: 60 req/hr cannot cover a scan. Abort.",
              file=sys.stderr)
        return 2
    started = time.time()
    candidates = search_candidates()
    entries = []
    checked = 0
    for repo in candidates:
        checked += 1
        try:
            rel, assets = pick_apk_release(repo["full_name"])
        except Exception as e:  # one bad repo must not kill the run
            print(f"skip {repo['full_name']}: {e}", flush=True)
            continue
        if rel is not None:
            entries.append(to_entry(repo, rel, assets))
            if len(entries) >= ENTRY_CAP:
                break
        time.sleep(0.3)
    entries.sort(key=lambda e: e["repo"]["stars"], reverse=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, "apps.json"), "w", encoding="utf-8") as f:
        json.dump(entries, f, ensure_ascii=False, indent=1)
    meta = {
        "schema": 1,
        "scanned_at": datetime.now(timezone.utc).isoformat(),
        "tool": "tools/scan.py",
        "queries": QUERIES,
        "candidates": len(candidates),
        "checked": checked,
        "matches": len(entries),
        "truncated": len(entries) >= ENTRY_CAP,
        "duration_s": round(time.time() - started, 1),
    }
    with open(os.path.join(OUT_DIR, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
    print(f"done: {checked} checked, {len(entries)} with APK releases", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
