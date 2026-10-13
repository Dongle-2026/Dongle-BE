import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("ai_evaluation", Path(__file__).parents[1] / "ai_evaluation.py")
evaluation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(evaluation)


class AiEvaluationTest(unittest.TestCase):
    def executed(self):
        data=evaluation.template(2)
        for i, case in enumerate(data["cases"]):
            case["articleId"] = i + 1
            for result in case["results"]:
                result.update(success=True, durationMs=100*(i+1), modelVersion="model", promptVersion="v1",
                              review={k:True for k in evaluation.CRITERIA})
        return data

    def test_unexecuted_template_and_unreviewed_successes_never_pass(self):
        report=evaluation.summarize(evaluation.template(20))
        self.assertFalse(report["allPassed"])
        self.assertIsNone(report["kinds"]["QUIZ"]["successRate"])
        data=self.executed();data["cases"][0]["results"][0]["review"]["facts"]=None
        report=evaluation.summarize(data,2)
        self.assertFalse(report["complete"])
        self.assertEqual(report["kinds"]["SUMMARY"]["unreviewedSuccesses"],1)

    def test_failures_and_quality_issues_are_counted_separately_and_latency_includes_failures(self):
        data=self.executed()
        data["cases"][0]["results"][0].update(success=False,durationMs=1000)
        data["cases"][1]["results"][1]["review"]["facts"]=False
        report=evaluation.summarize(data,2)
        self.assertTrue(report["complete"]);self.assertFalse(report["allPassed"])
        self.assertEqual(report["kinds"]["SUMMARY"]["successRate"],.5)
        self.assertEqual(report["kinds"]["SUMMARY"]["p95Ms"],1000)
        self.assertEqual(report["kinds"]["CARD"]["qualityFailed"],1)

    def test_complete_reviewed_dataset_passes_and_missing_cases_do_not(self):
        data=self.executed()
        self.assertTrue(evaluation.summarize(data,2)["allPassed"])
        self.assertFalse(evaluation.summarize(data,20)["allPassed"])

    def test_duplicate_cases_or_kinds_invalid_reviews_and_missing_provenance_are_rejected(self):
        data=self.executed();data["cases"][1]["id"]=data["cases"][0]["id"]
        with self.assertRaises(ValueError): evaluation.summarize(data,2)
        data=self.executed();data["cases"][0]["results"].append(data["cases"][0]["results"][0])
        with self.assertRaises(ValueError): evaluation.summarize(data,2)
        for field,value in [("durationMs",float("nan")),("success",1),("promptVersion",None),("review",{"facts":"yes"})]:
            data=self.executed();data["cases"][0]["results"][0][field]=value
            with self.assertRaises(ValueError): evaluation.summarize(data,2)

    def test_mixed_versions_cannot_pass_as_a_fixed_model_evaluation(self):
        data=self.executed();data["cases"][1]["results"][0]["modelVersion"]="other-model"
        report=evaluation.summarize(data,2)
        self.assertTrue(report["complete"])
        self.assertFalse(report["allPassed"])
        self.assertFalse(report["kinds"]["SUMMARY"]["versionsConsistent"])
