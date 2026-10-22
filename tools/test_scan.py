import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone

import scan
from scan import (
    QUERY_SETS,
    QUERY_SETS_PER_RUN,
    SCAN_STATE_FILE,
    interleave_candidates,
    load_queue,
    plan_run,
    query_set_indices,
    save_queue,
)


class CatalogScannerTest(unittest.TestCase):
    def test_scheduled_runs_cover_all_query_sets_within_seven_scans(self):
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        step = timedelta(seconds=scan.SCHEDULE_INTERVAL_SECONDS)
        scanned = set()
        for run in range(7):
            scanned.update(query_set_indices(start + step * run))

        self.assertEqual(set(range(len(QUERY_SETS))), scanned)

    def test_each_slot_selects_two_sets_and_advances_without_gaps(self):
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        step = timedelta(seconds=scan.SCHEDULE_INTERVAL_SECONDS)
        first = query_set_indices(start)
        second = query_set_indices(start + step)

        self.assertEqual(QUERY_SETS_PER_RUN, len(first))
        self.assertEqual(
            [(index + QUERY_SETS_PER_RUN) % len(QUERY_SETS) for index in first],
            second,
        )

    def test_candidate_interleave_deduplicates_and_fairly_caps_groups(self):
        groups = [
            [{"id": 1}, {"id": 2}, {"id": 3}],
            [{"id": 1}, {"id": 4}, {"id": 5}],
            [{"id": 6}],
        ]

        result = interleave_candidates(groups, 5)

        self.assertEqual([1, 4, 6, 2, 5], [repo["id"] for repo in result])


class ResumePlanTest(unittest.TestCase):
    def setUp(self):
        self.now = datetime(2026, 1, 1, 12, 0, tzinfo=timezone.utc)
        self.slot = int(self.now.timestamp() // scan.SCHEDULE_INTERVAL_SECONDS)

    def test_missing_state_starts_a_fresh_rotation_slot(self):
        plan = plan_run(None, self.now)

        self.assertEqual("fresh", plan["mode"])
        self.assertEqual(query_set_indices(self.now), plan["query_sets"])
        self.assertEqual([], plan["checked_ids"])

    def test_unfinished_state_wins_over_the_current_slot(self):
        state = {
            "slot": self.slot - 1,
            "query_sets": [7],
            "queries": ["android language:Rust archived:false fork:false"],
            "checked_ids": [11, 12],
            "complete": False,
        }

        plan = plan_run(state, self.now)

        self.assertEqual("resume", plan["mode"])
        self.assertEqual([7], plan["query_sets"])
        self.assertEqual([11, 12], plan["checked_ids"])

    def test_finished_state_for_the_same_slot_has_nothing_to_do(self):
        state = {
            "slot": self.slot,
            "query_sets": [0, 1],
            "queries": ["q"],
            "checked_ids": [],
            "complete": True,
        }

        self.assertEqual("done", plan_run(state, self.now)["mode"])

    def test_finished_state_from_an_older_slot_rotates_onward(self):
        state = {
            "slot": self.slot - 1,
            "query_sets": [0, 1],
            "queries": ["q"],
            "checked_ids": [],
            "complete": True,
        }

        self.assertEqual("fresh", plan_run(state, self.now)["mode"])


class QueueCacheTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.old_out_dir = scan.OUT_DIR
        scan.OUT_DIR = self.tmp.name

    def tearDown(self):
        scan.OUT_DIR = self.old_out_dir
        self.tmp.cleanup()

    def test_queue_round_trips_for_the_same_queries_only(self):
        queries = ["android music player archived:false fork:false"]
        save_queue([{"id": 1, "full_name": "a/b"}], queries)

        self.assertEqual([{"id": 1, "full_name": "a/b"}],
                         load_queue(queries))
        self.assertEqual([], load_queue(["different query"]))
        self.assertIsNone(scan.load_json(os.path.join(
            self.tmp.name, SCAN_STATE_FILE)))

    def test_write_json_is_atomic_and_round_trips(self):
        path = os.path.join(self.tmp.name, "payload.json")

        scan.write_json(path, {"a": [1, 2]})

        self.assertEqual({"a": [1, 2]}, scan.load_json(path))
        self.assertFalse(os.path.exists(path + ".tmp"))


class MainFlowTest(unittest.TestCase):
    """End-to-end: a stopped chunk checkpoints, the next run resumes, the
    third finds the slot already complete."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.saved = (scan.OUT_DIR, scan.TOKEN, scan.REQUEST_INTERVAL_S,
                      scan.STOP["requested"], scan.api_get,
                      scan.pick_apk_release)
        scan.OUT_DIR = self.tmp.name
        scan.TOKEN = "test-token"
        scan.REQUEST_INTERVAL_S = 0.0
        scan.STOP["requested"] = False
        self.picks = 0

        def fake_api_get(path):
            if "/search/repositories" in path:
                return 200, {"items": [self.repo(i) for i in (1, 2, 3)]}, {}
            return 200, [self.release()], {}

        def fake_pick(full_name):
            self.picks += 1
            if self.picks == 1:
                scan.STOP["requested"] = True  # simulate a job cancel
            return self.release(), self.release()["assets"]

        scan.api_get = fake_api_get
        scan.pick_apk_release = fake_pick

    def tearDown(self):
        (scan.OUT_DIR, scan.TOKEN, scan.REQUEST_INTERVAL_S,
         scan.STOP["requested"], scan.api_get,
         scan.pick_apk_release) = self.saved
        self.tmp.cleanup()

    @staticmethod
    def repo(repo_id):
        return {
            "id": repo_id,
            "full_name": f"owner/app{repo_id}",
            "owner": {"login": "owner", "avatar_url": None},
            "description": "an app",
            "stargazers_count": repo_id,
            "forks_count": 0,
            "topics": [],
            "default_branch": "main",
            "updated_at": "2026-01-01T00:00:00Z",
            "created_at": "2025-01-01T00:00:00Z",
            "license": {"spdx_id": "MIT"},
            "language": "Kotlin",
            "open_issues_count": 0,
            "subscribers_count": 0,
            "homepage": None,
            "pushed_at": "2026-01-01T00:00:00Z",
        }

    @staticmethod
    def release():
        return {
            "id": 900,
            "tag_name": "v1",
            "name": "v1",
            "body": "notes",
            "published_at": "2026-01-01T00:00:00Z",
            "html_url": "https://example.test/v1",
            "prerelease": False,
            "assets": [{
                "id": 1,
                "name": "app.apk",
                "content_type": "application/vnd.android.package-archive",
                "size": 10,
                "browser_download_url": "https://example.test/app.apk",
                "digest": None,
                "download_count": 0,
            }],
        }

    def read_state(self):
        return scan.load_json(os.path.join(self.tmp.name, SCAN_STATE_FILE))

    def read_apps(self):
        return scan.load_json(os.path.join(self.tmp.name, "apps.json"))

    def test_stop_checkpoints_then_next_run_resumes_and_completes(self):
        self.assertEqual(0, scan.main())
        state = self.read_state()
        self.assertFalse(state["complete"])
        self.assertEqual([3], state["checked_ids"])  # highest stars first
        self.assertEqual(1, len(self.read_apps()))
        self.assertTrue(os.path.exists(os.path.join(
            self.tmp.name, scan.QUEUE_FILE)))

        scan.STOP["requested"] = False
        self.assertEqual(0, scan.main())
        state = self.read_state()
        self.assertTrue(state["complete"])
        self.assertEqual([], state["checked_ids"])
        self.assertEqual(3, len(self.read_apps()))

        # Same slot again: no work, nothing rewritten.
        meta_before = scan.load_json(os.path.join(self.tmp.name, "meta.json"))
        self.assertEqual(0, scan.main())
        self.assertEqual(meta_before, scan.load_json(
            os.path.join(self.tmp.name, "meta.json")))


if __name__ == "__main__":
    unittest.main()
