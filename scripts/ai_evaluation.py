#!/usr/bin/env python3
"""고정 기사별 AI 생성 결과·사람 검수·지연 집계. 미실행/미검수를 합격으로 계산하지 않는다."""
import argparse
import json
import math
from pathlib import Path

KINDS = ("SUMMARY", "CARD", "QUIZ")
CRITERIA = ("facts", "numbers", "unambiguous")


def template(count):
    return {"cases": [{"id": f"case-{i:02}", "articleId": None,
            "results": [{"kind": kind, "success": None, "durationMs": None,
                         "modelVersion": None, "promptVersion": None,
                         "errorCode": None, "review": {key: None for key in CRITERIA}}
                        for kind in KINDS]} for i in range(1, count + 1)]}


def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)] if values else None


def summarize(document, expected_cases=20):
    cases = document.get("cases") if isinstance(document, dict) else None
    if not isinstance(cases, list) or not cases or expected_cases < 1:
        raise ValueError("cases must be a nonempty list and expected_cases must be positive")
    seen = set()
    articles = set()
    groups = {kind: [] for kind in KINDS}
    for case in cases:
        if not isinstance(case, dict) or not isinstance(case.get("id"), str) or not case["id"].strip() or case["id"] in seen:
            raise ValueError("each case must have a unique nonempty id")
        seen.add(case["id"])
        results = case.get("results")
        if not isinstance(results, list):
            raise ValueError("results must be a list")
        if any(isinstance(r, dict) and r.get("success") is not None for r in results):
            article = case.get("articleId")
            if type(article) not in (int, str) or not str(article).strip() or article in articles:
                raise ValueError("executed cases require distinct articleId values")
            if type(article) is int and article < 1:
                raise ValueError("articleId must be positive")
            articles.add(article)
        kinds = set()
        for result in results:
            if not isinstance(result, dict) or result.get("kind") not in KINDS or result["kind"] in kinds:
                raise ValueError("result kinds must be supported and unique within each case")
            kinds.add(result["kind"])
            success, duration = result.get("success"), result.get("durationMs")
            if success is not None and type(success) is not bool:
                raise ValueError("success must be boolean or null")
            if duration is not None and (type(duration) not in (int, float) or not math.isfinite(duration) or duration < 0):
                raise ValueError("durationMs must be a finite nonnegative number or null")
            review = result.get("review", {})
            if not isinstance(review, dict) or any(review.get(k) is not None and type(review[k]) is not bool for k in CRITERIA):
                raise ValueError("review criteria must be boolean or null")
            if success is not None and (duration is None or any(not isinstance(result.get(k), str) or not result[k].strip() for k in ("modelVersion", "promptVersion"))):
                raise ValueError("executed results require durationMs, modelVersion and promptVersion")
            groups[result["kind"]].append(result)
    report = {"expectedCases": expected_cases, "caseCount": len(cases), "complete": len(cases) == expected_cases, "kinds": {}}
    for kind, results in groups.items():
        attempted = [r for r in results if r.get("success") is not None]
        succeeded = [r for r in attempted if r["success"]]
        reviewed = [r for r in succeeded if all(type(r.get("review", {}).get(k)) is bool for k in CRITERIA)]
        passed = [r for r in reviewed if all(r["review"][k] for k in CRITERIA)]
        durations = [r["durationMs"] for r in attempted]  # 실패 지연도 포함한다.
        versions = sorted({(str(r["modelVersion"]), str(r["promptVersion"])) for r in attempted})
        complete = len(attempted) == expected_cases and len(succeeded) == len(reviewed) and len(results) == expected_cases
        report["complete"] = report["complete"] and complete
        report["kinds"][kind] = {"attempted": len(attempted), "succeeded": len(succeeded),
            "failed": len(attempted) - len(succeeded), "unexecuted": len(cases) - len(attempted),
            "successRate": len(succeeded) / len(attempted) if attempted else None,
            "reviewed": len(reviewed), "qualityPassed": len(passed), "qualityFailed": len(reviewed) - len(passed),
            "unreviewedSuccesses": len(succeeded) - len(reviewed),
            "qualityPassRate": len(passed) / len(reviewed) if reviewed else None,
            "p50Ms": percentile(durations, .5), "p95Ms": percentile(durations, .95),
            "versionsConsistent": len(versions) <= 1, "versions": [{"model": m, "prompt": p} for m, p in versions]}
    report["allPassed"] = report["complete"] and all(v["failed"] == 0 and v["qualityFailed"] == 0 and v["versionsConsistent"] for v in report["kinds"].values())
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--init", type=Path, help="미실행 평가표 신규 생성")
    mode.add_argument("--input", type=Path, help="실행·검수 결과 JSON")
    parser.add_argument("--expected-cases", type=int, default=20)
    args = parser.parse_args()
    if not 1 <= args.expected_cases <= 1000:
        parser.error("expected-cases must be 1..1000")
    try:
        if args.init:
            with args.init.open("x", encoding="utf-8") as output:
                json.dump(template(args.expected_cases), output, ensure_ascii=False, indent=2)
            print("Created an unexecuted evaluation template.")
            return 0
        if args.input.stat().st_size > 10_000_000:
            raise ValueError("input exceeds 10MB")
        report = summarize(json.loads(args.input.read_text(encoding="utf-8")), args.expected_cases)
        print(json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False))
        return 0 if report["allPassed"] else 2
    except (OSError, ValueError, TypeError, KeyError):
        parser.error("invalid evaluation file; check the documented format (file contents are not printed)")


if __name__ == "__main__":
    raise SystemExit(main())
