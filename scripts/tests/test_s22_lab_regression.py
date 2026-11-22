from unittest import TestCase

from aicall_tools.diagnostics.s22_lab_regression import (
    CODE, EN_TURN_TEXT, TURN_TEXT, measures,
)


class S22LabRegressionTest(TestCase):
    def test_equivalent_six_turn_english_fixture_preserves_literal_code(self):
        self.assertEqual(6, len(TURN_TEXT))
        self.assertEqual(6, len(EN_TURN_TEXT))
        self.assertIn(CODE, EN_TURN_TEXT[0])
        self.assertIn("purple", EN_TURN_TEXT[2])
        self.assertIn("Goodbye", EN_TURN_TEXT[5])

    def test_english_recall_measures_code_and_latest_color(self):
        result = measures(self.turns(f"{CODE}. The corrected color is purple."), "en-US")
        self.assertTrue(result["exact_code_found"])
        self.assertTrue(result["latest_color_found"])
        bad = measures(self.turns(f"{CODE}0. Purple or green."), "en-US")
        self.assertFalse(bad["exact_code_found"])
        self.assertFalse(bad["latest_color_found"])


    def turns(self, recall):
        return [{"turn": number, "elapsed_ms": 15, "assistant": recall if number == 4 else "OK"}
                for number in range(1, 5)]

    def test_exact_text_markers_do_not_grade_the_conversation(self):
        result = measures(self.turns(f"{CODE}. Kolor fioletowy."))
        self.assertTrue(result["exact_code_found"])
        self.assertEqual([4], result["exact_code_echo_on"])
        self.assertTrue(result["latest_color_found"])
        self.assertFalse(result["reply_after_override"])
        self.assertEqual("UNASSESSED", result["semantic_verdict"])

    def test_incomplete_or_incorrect_recall_is_explicit(self):
        self.assertIsNone(measures([])["exact_code_found"])
        result = measures(self.turns(f"{CODE}0. Fioletowy lub zielony."))
        self.assertFalse(result["exact_code_found"])
        self.assertFalse(result["latest_color_found"])

    def test_exact_echo_tracks_first_confirmation_and_preserves_case_and_boundaries(self):
        turns = self.turns(f"{CODE.lower()} lub prefiks-{CODE}. Kolor fioletowy.")
        turns[0]["assistant"] = f"Przyjmuję kod {CODE}."
        result = measures(turns)
        self.assertEqual([1], result["exact_code_echo_on"])
        self.assertFalse(result["exact_code_found"])
        self.assertTrue(result["latest_color_found"])
