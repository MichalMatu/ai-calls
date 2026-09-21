import unittest

from local_phone_llm_live_call import (
    EDGE_GALLERY_PROVIDER,
    LOCAL_PHONE_PROVIDER,
    ORANGE_ACTION_LIST_CAPABILITIES,
    ORANGE_SUPPORT_NUMBER,
    _is_ignorable_gate_c_preroll,
    _require_gate_c_fast_path_report,
    _require_gate_c_observation_report,
    build_probe_start_args,
    normalize_allowlisted_target,
    normalize_provider,
    parse_probe_report,
)


class LocalPhoneLlmLiveCallTest(unittest.TestCase):
    def test_orange_support_is_the_only_initial_allowlisted_target(self):
        self.assertEqual("510100100", ORANGE_SUPPORT_NUMBER)
        self.assertEqual("510100100", normalize_allowlisted_target("510 100 100"))
        with self.assertRaises(ValueError):
            normalize_allowlisted_target("501234567")
        with self.assertRaises(ValueError):
            normalize_allowlisted_target("112")

    def test_probe_args_can_select_edge_gallery_without_dialing(self):
        args = build_probe_start_args("RFCT70L7E8J", EDGE_GALLERY_PROVIDER)
        joined = " ".join(args)
        self.assertIn("run_local_phone_llm_live_call_probe", joined)
        self.assertIn("text_llm_provider EDGE_GALLERY", joined)
        self.assertNotIn(ORANGE_SUPPORT_NUMBER, joined)

    def test_gate_c_probe_args_require_explicit_allowlisted_target(self):
        args = build_probe_start_args(
            "RFCT70L7E8J",
            LOCAL_PHONE_PROVIDER,
            gate_c_fast_path=True,
            target=ORANGE_SUPPORT_NUMBER,
        )
        joined = " ".join(args)
        self.assertIn("gate_c_fast_path true", joined)
        self.assertIn(f"live_call_target {ORANGE_SUPPORT_NUMBER}", joined)
        with self.assertRaises(ValueError):
            build_probe_start_args(
                "RFCT70L7E8J",
                LOCAL_PHONE_PROVIDER,
                gate_c_fast_path=True,
                target="501234567",
            )

    def test_gate_c_probe_args_can_select_only_named_orange_explorer_action(self):
        args = build_probe_start_args(
            "RFCT70L7E8J",
            LOCAL_PHONE_PROVIDER,
            gate_c_fast_path=True,
            target=ORANGE_SUPPORT_NUMBER,
            orange_action=ORANGE_ACTION_LIST_CAPABILITIES,
        )
        joined = " ".join(args)
        self.assertIn("orange_live_action list_capabilities", joined)

        with self.assertRaises(ValueError):
            build_probe_start_args(
                "RFCT70L7E8J",
                LOCAL_PHONE_PROVIDER,
                gate_c_fast_path=True,
                target=ORANGE_SUPPORT_NUMBER,
                orange_action="arbitrary_speech",
            )

    def test_provider_selection_is_fail_closed(self):
        self.assertEqual(LOCAL_PHONE_PROVIDER, normalize_provider(LOCAL_PHONE_PROVIDER))
        self.assertEqual(EDGE_GALLERY_PROVIDER, normalize_provider(EDGE_GALLERY_PROVIDER))
        with self.assertRaises(ValueError):
            normalize_provider("OPENAI_TEXT")
        with self.assertRaises(ValueError):
            normalize_provider("unknown")

    def test_report_parser_requires_terminal_marker(self):
        self.assertIsNone(parse_probe_report("stt_text=test\n"))
        report = parse_probe_report(
            "text_llm_provider=EDGE_GALLERY\nstt_text=witaj\napproved_text=dzień dobry\n"
            "local_text_llm_live_call_success=true\n"
            "local_phone_llm_live_call_success=true\nprobe_complete=true\n"
        )
        self.assertIsNotNone(report)
        self.assertEqual("EDGE_GALLERY", report["text_llm_provider"])
        self.assertEqual("witaj", report["stt_text"])
        self.assertEqual("dzień dobry", report["approved_text"])
        self.assertEqual("true", report["local_text_llm_live_call_success"])

    def test_gate_c_report_requires_bound_plan_zero_backend_generation_and_exact_reply(self):
        _require_gate_c_fast_path_report({
            "gate_c_fast_path": "true",
            "gate_c_call_plan_bound": "true",
            "orange_live_action": "greeting",
            "backend_generate_calls": "0",
            "approved_text": "Dzień dobry.",
        })

        invalid_reports = [
            {
                "gate_c_fast_path": "false",
                "gate_c_call_plan_bound": "true",
                "orange_live_action": "greeting",
                "backend_generate_calls": "0",
                "approved_text": "Dzień dobry.",
            },
            {
                "gate_c_fast_path": "true",
                "gate_c_call_plan_bound": "false",
                "orange_live_action": "greeting",
                "backend_generate_calls": "0",
                "approved_text": "Dzień dobry.",
            },
            {
                "gate_c_fast_path": "true",
                "gate_c_call_plan_bound": "true",
                "orange_live_action": "greeting",
                "backend_generate_calls": "1",
                "approved_text": "Dzień dobry.",
            },
            {
                "gate_c_fast_path": "true",
                "gate_c_call_plan_bound": "true",
                "orange_live_action": "greeting",
                "backend_generate_calls": "0",
                "approved_text": "inna odpowiedź",
            },
        ]
        for report in invalid_reports:
            with self.assertRaises(RuntimeError):
                _require_gate_c_fast_path_report(report)

    def test_gate_c_can_retry_only_exact_observed_orange_preroll(self):
        observed_preroll = {
            "gate_c_fast_path": "true",
            "gate_c_call_plan_bound": "true",
            "backend_generate_calls": "0",
            "local_text_llm_live_call_success": "false",
            "failure_reason": "gate_c_take_over",
            "stt_text": "orange",
        }
        self.assertTrue(_is_ignorable_gate_c_preroll(observed_preroll))

        neighboring_fail_closed_reports = [
            {**observed_preroll, "stt_text": "orange dzień dobry"},
            {**observed_preroll, "stt_text": "proszę podać pesel"},
            {**observed_preroll, "failure_reason": "probe_timeout"},
            {**observed_preroll, "backend_generate_calls": "1"},
            {**observed_preroll, "gate_c_call_plan_bound": "false"},
        ]
        for report in neighboring_fail_closed_reports:
            self.assertFalse(_is_ignorable_gate_c_preroll(report), report)

    def test_gate_c_observation_requires_takeover_zero_backend_and_nonblank_transcript(self):
        report = {
            "gate_c_fast_path": "true",
            "gate_c_call_plan_bound": "true",
            "orange_live_action": "observe_only",
            "backend_generate_calls": "0",
            "local_text_llm_live_call_success": "false",
            "failure_reason": "gate_c_take_over",
            "stt_text": "mam kilka możliwości",
            "stt_transcript_nonblank": "true",
            "endpointing": "trailing_silence",
            "endpoint_reason": "trailing_silence",
            "endpoint_speech_detected": "true",
            "endpoint_capture_ms": "4200",
        }
        _require_gate_c_observation_report(report)

        for invalid in [
            {**report, "backend_generate_calls": "1"},
            {**report, "failure_reason": "probe_timeout"},
            {**report, "stt_text": ""},
            {**report, "orange_live_action": "list_capabilities"},
        ]:
            with self.assertRaises(RuntimeError):
                _require_gate_c_observation_report(invalid)


if __name__ == "__main__":
    unittest.main()
