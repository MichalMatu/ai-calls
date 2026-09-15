import unittest

from s22_call_control import (
    call_state_from_registry,
    center_from_bounds,
    find_node_bounds,
    normalize_dtmf,
)


SAMPLE_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node text="" content-desc="Klawiatura" bounds="[100,200][300,400]" />
  <node text="1" content-desc="1" bounds="[10,20][110,120]" />
  <node text="#" content-desc="krzyżyk" bounds="[210,420][310,520]" />
</hierarchy>
"""


class CallControlParsingTest(unittest.TestCase):
    def test_extracts_primary_call_state(self):
        registry = "    mCallState=2\n    mCallState=0\n"
        self.assertEqual(2, call_state_from_registry(registry))

    def test_returns_none_when_call_state_missing(self):
        self.assertIsNone(call_state_from_registry("no state here"))

    def test_calculates_bounds_center(self):
        self.assertEqual((200, 300), center_from_bounds("[100,200][300,400]"))

    def test_finds_polish_keypad_by_content_description(self):
        self.assertEqual(
            "[100,200][300,400]",
            find_node_bounds(SAMPLE_XML, ["klawiatura", "keypad", "dialpad"]),
        )

    def test_finds_exact_digit_text(self):
        self.assertEqual("[10,20][110,120]", find_node_bounds(SAMPLE_XML, ["1"], exact=True))

    def test_normalizes_valid_dtmf_sequence(self):
        self.assertEqual("12#*0", normalize_dtmf("12 #*0"))

    def test_rejects_invalid_dtmf_character(self):
        with self.assertRaises(ValueError):
            normalize_dtmf("12A")


if __name__ == "__main__":
    unittest.main()
