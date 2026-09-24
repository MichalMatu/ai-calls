import json
import unittest
from unittest import mock


class RealtimeCredentialBrokerTest(unittest.TestCase):
    def setUp(self):
        from aicall_tools.realtime.realtime_credential_broker import BrokerConfig

        self.BrokerConfig = BrokerConfig

    def test_config_requires_server_side_openai_key_and_strong_client_token(self):
        with self.assertRaises(ValueError):
            self.BrokerConfig.from_env({})
        with self.assertRaises(ValueError):
            self.BrokerConfig.from_env(
                {
                    "OPENAI_API_KEY": "sk-server-only",
                    "AI_CALL_BRIDGE_BROKER_TOKEN": "too-short",
                }
            )

        config = self.BrokerConfig.from_env(
            {
                "OPENAI_API_KEY": "sk-server-only",
                "AI_CALL_BRIDGE_BROKER_TOKEN": "x" * 32,
            }
        )
        self.assertEqual("gpt-realtime-2.1", config.model)
        self.assertNotIn("sk-server-only", repr(config))
        self.assertNotIn("x" * 32, repr(config))

    def test_upstream_request_uses_official_ga_endpoint_and_server_only_headers(self):
        from aicall_tools.realtime.realtime_credential_broker import build_openai_request

        config = self.BrokerConfig(
            openai_api_key="sk-server-only",
            client_bearer_token="b" * 32,
            model="gpt-realtime-2.1",
            safety_identifier="hashed-user-123",
        )

        request = build_openai_request(config)

        self.assertEqual("POST", request.get_method())
        self.assertEqual("https://api.openai.com/v1/realtime/client_secrets", request.full_url)
        self.assertEqual("Bearer sk-server-only", request.get_header("Authorization"))
        self.assertEqual("application/json", request.get_header("Content-type"))
        self.assertEqual("hashed-user-123", request.get_header("Openai-safety-identifier"))
        self.assertEqual(
            {"session": {"type": "realtime", "model": "gpt-realtime-2.1"}},
            json.loads(request.data.decode("utf-8")),
        )

    def test_mint_returns_only_minimal_android_payload(self):
        from aicall_tools.realtime.realtime_credential_broker import mint_client_secret

        config = self.BrokerConfig(
            openai_api_key="sk-server-only",
            client_bearer_token="b" * 32,
            model="gpt-realtime-2.1",
            safety_identifier=None,
        )
        response = mock.MagicMock()
        response.status = 200
        response.read.return_value = json.dumps(
            {
                "value": "ek_short_lived",
                "expires_at": 2_000_000_000,
                "id": "sess_should_not_leak",
                "instructions": "server metadata should not leak",
            }
        ).encode("utf-8")
        response.__enter__.return_value = response
        response.__exit__.return_value = False
        opener = mock.Mock(return_value=response)

        payload = mint_client_secret(config, opener=opener)

        self.assertEqual(
            {"value": "ek_short_lived", "expires_at": 2_000_000_000},
            payload,
        )
        opener.assert_called_once()

    def test_invalid_upstream_payload_fails_without_echoing_secret_or_body(self):
        from aicall_tools.realtime.realtime_credential_broker import BrokerUpstreamError, mint_client_secret

        config = self.BrokerConfig(
            openai_api_key="sk-server-only",
            client_bearer_token="b" * 32,
            model="gpt-realtime-2.1",
            safety_identifier=None,
        )
        response = mock.MagicMock()
        response.status = 200
        response.read.return_value = b'{"value":"ek_sensitive_missing_expiry"}'
        response.__enter__.return_value = response
        response.__exit__.return_value = False

        with self.assertRaises(BrokerUpstreamError) as caught:
            mint_client_secret(config, opener=mock.Mock(return_value=response))

        text = str(caught.exception)
        self.assertNotIn("ek_sensitive", text)
        self.assertNotIn("sk-server-only", text)

    def test_client_auth_uses_exact_bearer_token_and_never_accepts_openai_key(self):
        from aicall_tools.realtime.realtime_credential_broker import is_client_authorized

        expected = "broker-token-" + "x" * 24
        self.assertTrue(is_client_authorized("Bearer " + expected, expected))
        self.assertFalse(is_client_authorized("Bearer wrong-token-" + "x" * 24, expected))
        self.assertFalse(is_client_authorized("Bearer sk-server-only", expected))
        self.assertFalse(is_client_authorized(None, expected))

    def test_request_body_accepts_only_empty_json_object(self):
        from aicall_tools.realtime.realtime_credential_broker import validate_client_body

        validate_client_body(b"")
        validate_client_body(b"{}")
        with self.assertRaises(ValueError):
            validate_client_body(b'{"model":"attacker-selected"}')
        with self.assertRaises(ValueError):
            validate_client_body(b"[]")
        with self.assertRaises(ValueError):
            validate_client_body(b"{" + b"x" * 4096 + b"}")


if __name__ == "__main__":
    unittest.main()
