import unittest
from datetime import datetime, timedelta, timezone

from scan import (
    QUERY_SETS,
    QUERY_SETS_PER_RUN,
    interleave_candidates,
    query_set_indices,
)


class CatalogScannerTest(unittest.TestCase):
    def test_scheduled_runs_cover_all_query_sets_within_five_scans(self):
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        scanned = set()
        for run in range(5):
            scanned.update(query_set_indices(start + timedelta(hours=3 * run)))

        self.assertEqual(set(range(len(QUERY_SETS))), scanned)

    def test_each_slot_selects_three_sets_and_advances_without_gaps(self):
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        first = query_set_indices(start)
        second = query_set_indices(start + timedelta(hours=3))

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


if __name__ == "__main__":
    unittest.main()
