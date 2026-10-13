import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("api_smoke", Path(__file__).parents[1] / "api_smoke.py")
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class ApiSmokeTest(unittest.TestCase):
    def test_complete_run_checks_authenticated_body_without_sending_token_to_public_routes(self):
        calls = []

        def request(base, path, token):
            calls.append((path, token))
            if path == "/api/health":
                data = {"status": "UP", "schema": "ready"}
            elif "limit=" in path:
                data = [{"id": "42"}]
            elif "size=" in path:
                data = {"content": []}
            elif "/body" in path:
                data = {"sections": [], "quizzes": []}
            elif "/stats" in path:
                data = {"totalHoney": 0, "todayHoney": 0, "correctCount": 0}
            elif "/activity" in path:
                data = [{"date": "2026-10-12", "level": 0, "honey": 0}] * 7
            else:
                data = {"totalQuizCount": 0, "interestedCategories": [], "weakCategories": []}
            return 200, {"status": 200, "data": data}

        results = smoke.run_checks("http://localhost", "private-token", request=request)
        self.assertTrue(all(r["result"] == "PASS" for r in results))
        self.assertEqual([t for p, t in calls if "/body" in p], [None, "private-token"])
        self.assertNotIn("private-token", str(results))

    def test_errors_and_missing_credentials_cannot_be_reported_as_success(self):
        results = smoke.run_checks("http://localhost", request=lambda *args: (500, None))
        self.assertTrue(any(r["result"] == "FAIL" for r in results))
        self.assertEqual(sum(r["result"] == "SKIP" for r in results), 4)
        invalid = smoke.run_checks("http://localhost", "token", request=lambda *args: (200, {"status": 200, "data": "wrong shape"}))
        self.assertTrue(any(r["result"] == "FAIL" for r in invalid))

    def test_invalid_article_id_is_never_used_as_a_path(self):
        calls = []

        def request(base, path, token):
            calls.append(path)
            return 200, {"status": 200, "data": []}

        results = smoke.run_checks("http://localhost", article_id="../admin", request=request)
        self.assertFalse(any("admin" in path for path in calls))
        self.assertTrue(any(r.get("reason") == "article_id_required" for r in results))


if __name__ == "__main__":
    unittest.main()
