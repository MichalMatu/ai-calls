from unittest import TestCase

from aicall_tools.diagnostics.s22_lab_regression import CODE, measures


class S22LabRegressionTest(TestCase):
    def turns(self, recall):
        return [{"turn": number, "elapsed_ms": 15, "assistant": recall if number == 4 else "OK"}
                for number in range(1, 5)]

    def test_exact_text_markers_do_not_grade_the_conversation(self):
        result = measures(self.turns(f"{CODE}. Kolor fioletowy."))
        self.assertTrue(result["exact_code_found"])
        self.assertTrue(result["latest_color_found"])
        self.assertFalse(result["reply_after_override"])
        self.assertEqual("UNASSESSED", result["semantic_verdict"])

    def test_incomplete_or_incorrect_recall_is_explicit(self):
        self.assertIsNone(measures([])["exact_code_found"])
        result = measures(self.turns(f"{CODE}0. Fioletowy lub zielony."))
        self.assertFalse(result["exact_code_found"])
        self.assertFalse(result["latest_color_found"])
