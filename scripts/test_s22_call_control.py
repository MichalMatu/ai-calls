import tempfile
import unittest
import wave
from pathlib import Path

from s22_call_control import (
    call_state_from_registry,
    center_from_bounds,
    find_node_bounds,
    normalize_dtmf,
    select_target_serial,
    write_pcm16_wav,
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

    def test_wraps_pcm16_as_mono_16khz_wav(self):
        pcm = b"\x01\x00\xff\xff\x00\x00\x10\x00"
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "capture.wav"
            write_pcm16_wav(pcm, output)
            with wave.open(str(output), "rb") as wav:
                self.assertEqual(1, wav.getnchannels())
                self.assertEqual(2, wav.getsampwidth())
                self.assertEqual(16000, wav.getframerate())
                self.assertEqual(4, wav.getnframes())
                self.assertEqual(pcm, wav.readframes(4))

    def test_rejects_odd_pcm_byte_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                write_pcm16_wav(b"\x00", Path(tmp) / "capture.wav")

    def test_selects_wireless_transport_when_usb_and_wifi_are_same_s22(self):
        devices = """List of devices attached
RFCT70L7E8J            device usb:0-1.2 product:g0sxeea model:SM_S906B device:g0s transport_id:5
192.168.0.100:34771    device product:g0sxeea model:SM_S906B device:g0s transport_id:1
"""
        self.assertEqual("192.168.0.100:34771", select_target_serial(devices))

    def test_selects_usb_when_it_is_the_only_s22_transport(self):
        devices = """List of devices attached
RFCT70L7E8J device usb:0-1.2 product:g0sxeea model:SM_S906B device:g0s transport_id:5
"""
        self.assertEqual("RFCT70L7E8J", select_target_serial(devices))

    def test_ignores_offline_wireless_transport(self):
        devices = """List of devices attached
192.168.0.100:34771 offline product:g0sxeea model:SM_S906B device:g0s transport_id:1
RFCT70L7E8J device usb:0-1.2 product:g0sxeea model:SM_S906B device:g0s transport_id:5
"""
        self.assertEqual("RFCT70L7E8J", select_target_serial(devices))

    def test_fails_if_no_target_s22_is_connected(self):
        devices = "List of devices attached\nemulator-5554 device model:sdk_gphone64_arm64\n"
        with self.assertRaises(RuntimeError):
            select_target_serial(devices)


if __name__ == "__main__":
    unittest.main()
