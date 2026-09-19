#!/usr/bin/env python3

import io
import json
import unittest
import urllib.request

import openai_text_broker as broker


class FakeResponse:
    def __init__(self, payload: dict, status: int = 200):
        self.status = status
        self._body = io.BytesIO(json.dumps(payload).encode("utf-8"))

    def read(self, size: int = -1) -> bytes:
        return self._body.read(size)

    def __enter__(self):
        return self

    def __exit__(self, *_args):
        return False


class TextBrokerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.config = broker.TextBrokerConfig(
            "sk-test-host-only-key",
            "android-session-token-abcdefghijklmnopqrstuvwxyz",
        )

    def test_build_request_uses_responses_api_and_privacy_controls(self) -> None:
        request = broker.build_openai_request(self.config, "Dzień dobry")
        self.assertEqual(request.full_url, broker.OPENAI_RESPONSES_URL)
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(request.get_header("Authorization"), "Bearer sk-test-host-only-key")
        payload = json.loads(request.data.decode("utf-8"))
        self.assertEqual(payload["model"], broker.DEFAULT_MODEL)
        self.assertEqual(payload["reasoning"], {"effort": "none"})
        self.assertEqual(payload["input"], "Dzień dobry")
        self.assertEqual(payload["max_output_tokens"], broker.MAX_OUTPUT_TOKENS)
        self.assertIs(payload["store"], False)
        self.assertIn("Autoryzacja zobowiązań należy do aplikacji", payload["instructions"])
        self.assertNotIn(self.config.client_bearer_token, request.data.decode("utf-8"))

    def test_generate_extracts_output_text_from_message_items(self) -> None:
        payload = {
            "status": "completed",
            "output": [
                {"type": "reasoning", "summary": []},
                {
                    "type": "message",
                    "role": "assistant",
                    "content": [
                        {"type": "output_text", "text": " Dzień dobry, w czym mogę pomóc? "},
                    ],
                },
            ],
        }

        def opener(request: urllib.request.Request, timeout: int):
            self.assertEqual(timeout, 20)
            self.assertNotIn(self.config.client_bearer_token, request.get_header("Authorization"))
            return FakeResponse(payload)

        self.assertEqual(
            broker.generate_text(self.config, "orange dzień dobry", opener=opener),
            "Dzień dobry, w czym mogę pomóc?",
        )

    def test_client_body_is_narrow_and_bounded(self) -> None:
        self.assertEqual(broker.parse_client_body(b'{"input":"  halo  "}'), "halo")
        for invalid in (
            b"{}",
            b'{"input":"x","model":"gpt-5.6"}',
            b'{"input":7}',
            b'{"input":""}',
        ):
            with self.assertRaises(ValueError):
                broker.parse_client_body(invalid)

    def test_client_bearer_must_be_distinct_and_never_be_openai_key(self) -> None:
        with self.assertRaises(ValueError):
            broker.TextBrokerConfig("sk-same-secret-abcdefghijklmnopqrstuvwxyz", "sk-same-secret-abcdefghijklmnopqrstuvwxyz")
        self.assertTrue(
            broker.is_client_authorized(
                "Bearer android-session-token-abcdefghijklmnopqrstuvwxyz",
                "android-session-token-abcdefghijklmnopqrstuvwxyz",
            )
        )
        self.assertFalse(
            broker.is_client_authorized(
                "Bearer wrong-session-token-abcdefghijklmnopqrstuvwxyz",
                "android-session-token-abcdefghijklmnopqrstuvwxyz",
            )
        )

    def test_repr_redacts_both_secrets(self) -> None:
        rendered = repr(self.config)
        self.assertNotIn(self.config.openai_api_key, rendered)
        self.assertNotIn(self.config.client_bearer_token, rendered)
        self.assertIn("REDACTED", rendered)


if __name__ == "__main__":
    unittest.main()
