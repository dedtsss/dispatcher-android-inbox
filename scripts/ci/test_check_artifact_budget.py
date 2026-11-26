import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_artifact_budget import BudgetError, verify_budget


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False

    def read(self):
        return json.dumps(self.payload).encode()


class ArtifactBudgetTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "candidate.apk"
        self.path.write_bytes(b"x" * 100)

    def tearDown(self):
        self.temp.cleanup()

    def response(self, payload):
        return FakeResponse(payload)

    def test_within_limit_counts_active_artifacts_and_archive_cushion(self):
        payload = {"total_count": 2, "artifacts": [
            {"id": 1, "size_in_bytes": 1000, "expired": False},
            {"id": 2, "size_in_bytes": 5000, "expired": True},
        ]}
        with patch("check_artifact_budget.urllib.request.urlopen", return_value=self.response(payload)):
            existing, proposed, projected = verify_budget(
                "owner/repo", "token", [str(self.path)], 1_100_000, "https://api.example", 1
            )
        self.assertEqual((existing, proposed, projected), (1000, 100, 1_049_676))

    def test_over_limit_fails_closed(self):
        payload = {"total_count": 1, "artifacts": [{"id": 1, "size_in_bytes": 1000, "expired": False}]}
        with patch("check_artifact_budget.urllib.request.urlopen", return_value=self.response(payload)):
            with self.assertRaises(BudgetError):
                verify_budget("owner/repo", "token", [str(self.path)], 1_000_000, "https://api.example")

    def test_incomplete_page_fails_closed(self):
        payload = {"total_count": 2, "artifacts": [{"id": 1, "size_in_bytes": 1, "expired": False}]}
        with patch("check_artifact_budget.urllib.request.urlopen", return_value=self.response(payload)):
            with self.assertRaises(BudgetError):
                verify_budget("owner/repo", "token", [str(self.path)], 10_000_000, "https://api.example")

    def test_changed_page_count_fails_closed(self):
        first_page = {
            "total_count": 101,
            "artifacts": [
                {"id": item, "size_in_bytes": 1, "expired": False}
                for item in range(1, 101)
            ],
        }
        second_page = {
            "total_count": 102,
            "artifacts": [{"id": 101, "size_in_bytes": 1, "expired": False}],
        }
        with patch(
            "check_artifact_budget.urllib.request.urlopen",
            side_effect=[self.response(first_page), self.response(second_page)],
        ):
            with self.assertRaises(BudgetError):
                verify_budget("owner/repo", "token", [str(self.path)], 10_000_000, "https://api.example")

    def test_missing_credentials_fail_closed(self):
        with self.assertRaises(BudgetError):
            verify_budget("owner/repo", "", [str(self.path)], 10_000_000, "https://api.example")


if __name__ == "__main__":
    unittest.main()
