#!/usr/bin/env python3
"""5개 연동 API 진단. 표준 라이브러리만 사용, 토큰/응답 본문은 출력하지 않는다."""
import argparse
import json
import os
import re
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, Request, build_opener


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None  # 다른 서버에 Authorization이 전달되지 않도록 리다이렉트는 실패로 처리


def fetch(base, path, token=None):
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(base + path, headers=headers)
    try:
        with build_opener(NoRedirect()).open(request, timeout=15) as response:
            raw = response.read(1_048_577)
            if len(raw) > 1_048_576:
                return response.status, None
            try:
                return response.status, json.loads(raw)
            except (ValueError, UnicodeError):
                return response.status, None
    except HTTPError as error:
        status = error.code
        error.close()
        return status, None
    except (URLError, TimeoutError, OSError):
        return 0, None


def run_checks(base, token=None, article_id=None, request=fetch):
    results = []

    def check(path, authenticated, validate):
        if authenticated and not token:
            results.append({"path": path, "result": "SKIP", "reason": "access_token_required"})
            return None
        status, body = request(base, path, token if authenticated else None)
        valid = status == 200 and isinstance(body, dict) and body.get("status") == 200 and validate(body.get("data"))
        results.append({"path": path, "httpStatus": status, "result": "PASS" if valid else "FAIL"})
        return body.get("data") if valid else None

    check("/api/health", False, lambda d: isinstance(d, dict) and d.get("status") == "UP"
          and d.get("schema") == "ready")
    news = check("/api/news?page=1&limit=10", False, lambda d: isinstance(d, list))
    check("/api/news?size=10&page=0", False, lambda d: isinstance(d, dict) and isinstance(d.get("content"), list))
    if article_id is None and news:
        article_id = news[0].get("id") if isinstance(news[0], dict) else None
    if article_id is not None and re.fullmatch(r"[1-9][0-9]*", str(article_id)):
        check(f"/api/news/{article_id}/body", False,
              lambda d: isinstance(d, dict) and isinstance(d.get("sections"), list) and isinstance(d.get("quizzes"), list))
        if token:
            check(f"/api/news/{article_id}/body", True,
                  lambda d: isinstance(d, dict) and isinstance(d.get("sections"), list) and isinstance(d.get("quizzes"), list))
    else:
        results.append({"path": "/api/news/{articleId}/body", "result": "SKIP", "reason": "article_id_required"})
    check("/api/me/stats", True, lambda d: isinstance(d, dict) and all(
        type(d.get(k)) is int and d[k] >= 0 for k in ("totalHoney", "todayHoney", "correctCount")))
    check("/api/me/activity?days=7", True, lambda d: isinstance(d, list) and len(d) == 7 and all(
        isinstance(day, dict) and isinstance(day.get("date"), str) and type(day.get("level")) is int
        and 0 <= day["level"] <= 4 and type(day.get("honey")) is int and day["honey"] >= 0 for day in d))
    check("/api/me/insights", True, lambda d: isinstance(d, dict) and type(d.get("totalQuizCount")) is int
          and isinstance(d.get("interestedCategories"), list) and isinstance(d.get("weakCategories"), list))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--article-id", type=int)
    args = parser.parse_args()
    base = args.base_url.rstrip("/")
    url = urlsplit(base)
    if url.scheme not in ("http", "https") or not url.hostname or url.username or url.password or url.query or url.fragment:
        parser.error("base-url must be an HTTP(S) URL without credentials, query, or fragment")
    if args.article_id is not None and args.article_id < 1:
        parser.error("article-id must be positive")
    token = os.environ.get("API_ACCESS_TOKEN")
    if token and (len(token) > 8192 or any(c.isspace() for c in token)):
        parser.error("API_ACCESS_TOKEN must be a token without whitespace")
    results = run_checks(base, token, args.article_id)
    print(json.dumps(results, ensure_ascii=False, indent=2))
    return 1 if any(r["result"] == "FAIL" for r in results) else 2 if any(r["result"] == "SKIP" for r in results) else 0


if __name__ == "__main__":
    sys.exit(main())
