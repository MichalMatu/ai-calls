import subprocess
import unittest
import urllib.error
from types import SimpleNamespace
from unittest import mock


class RealtimeOffcallLabTest(unittest.TestCase):
    def test_requires_only_host_api_key_from_operator(self):
        from aicall_tools.realtime.realtime_offcall_lab import require_openai_api_key

        with self.assertRaises(ValueError):
            require_openai_api_key({})
        self.assertEqual(
            "sk-host-only",
            require_openai_api_key({"OPENAI_API_KEY": "  sk-host-only  "}),
        )

    def test_generated_broker_token_is_strong_and_not_openai_shaped(self):
        from aicall_tools.realtime.realtime_offcall_lab import generate_broker_token

        token = generate_broker_token()

        self.assertGreaterEqual(len(token), 32)
        self.assertFalse(token.lower().startswith("sk-"))

    def test_child_environments_keep_long_lived_key_broker_only(self):
        from aicall_tools.realtime.realtime_offcall_lab import (
            build_broker_environment,
            build_smoke_environment,
            build_tunnel_environment,
        )

        host = {
            "PATH": "/bin",
            "OPENAI_API_KEY": "sk-host-secret",
            "AI_CALL_BRIDGE_BROKER_TOKEN": "stale-token",
            "AI_CALL_BRIDGE_BROKER_HTTPS_URL": "https://stale.invalid",
        }
        token = "broker-" + "x" * 40
        endpoint = "https://example.trycloudflare.com/v1/realtime/client-secret"

        broker = build_broker_environment(host, api_key="sk-host-secret", broker_token=token)
        tunnel = build_tunnel_environment(host)
        smoke = build_smoke_environment(
            host,
            broker_token=token,
            credential_endpoint=endpoint,
        )

        self.assertEqual("sk-host-secret", broker["OPENAI_API_KEY"])
        self.assertEqual(token, broker["AI_CALL_BRIDGE_BROKER_TOKEN"])
        self.assertNotIn("AI_CALL_BRIDGE_BROKER_HTTPS_URL", broker)

        self.assertNotIn("OPENAI_API_KEY", tunnel)
        self.assertNotIn("AI_CALL_BRIDGE_BROKER_TOKEN", tunnel)
        self.assertNotIn("AI_CALL_BRIDGE_BROKER_HTTPS_URL", tunnel)

        self.assertNotIn("OPENAI_API_KEY", smoke)
        self.assertEqual(token, smoke["AI_CALL_BRIDGE_BROKER_TOKEN"])
        self.assertEqual(endpoint, smoke["AI_CALL_BRIDGE_BROKER_HTTPS_URL"])

    def test_child_environments_do_not_forward_unrelated_host_secrets(self):
        from aicall_tools.realtime.realtime_offcall_lab import (
            build_broker_environment, build_smoke_environment, build_tunnel_environment
        )
        host = {
            "PATH": "/bin", "HOME": "/tmp/home",
            "OPENAI_API_KEY": "sk-host-secret",
            "OPENAI_REALTIME_MODEL": "gpt-realtime-2.1",
            "GITHUB_TOKEN": "must-not-leak",
            "AWS_SECRET_ACCESS_KEY": "must-not-leak",
        }
        token = "broker-" + "x" * 40
        endpoint = "https://quiet-moon.trycloudflare.com/v1/realtime/client-secret"
        children = (
            build_broker_environment(host, api_key=host["OPENAI_API_KEY"], broker_token=token),
            build_tunnel_environment(host),
            build_smoke_environment(host, broker_token=token, credential_endpoint=endpoint),
        )
        for child in children:
            self.assertNotIn("GITHUB_TOKEN", child)
            self.assertNotIn("AWS_SECRET_ACCESS_KEY", child)
        self.assertEqual("gpt-realtime-2.1", children[0]["OPENAI_REALTIME_MODEL"])
        self.assertNotIn("OPENAI_REALTIME_MODEL", children[1])
        self.assertNotIn("OPENAI_REALTIME_MODEL", children[2])

    def test_tunnel_parser_accepts_only_https_trycloudflare_host(self):
        from aicall_tools.realtime.realtime_offcall_lab import parse_quick_tunnel_url

        self.assertEqual(
            "https://quiet-moon.trycloudflare.com",
            parse_quick_tunnel_url(
                "INF +-------------------------------- https://quiet-moon.trycloudflare.com"
            ),
        )
        self.assertIsNone(parse_quick_tunnel_url("http://quiet-moon.trycloudflare.com"))
        self.assertIsNone(parse_quick_tunnel_url("https://trycloudflare.com.evil.test"))
        self.assertIsNone(
            parse_quick_tunnel_url("https://quiet-moon.trycloudflare.com.evil.test")
        )
        self.assertIsNone(parse_quick_tunnel_url("https://example.com"))

    def test_public_readiness_retries_transport_and_requires_unauthorized_boundary(self):
        from aicall_tools.realtime.realtime_offcall_lab import wait_for_public_broker

        endpoint = "https://quiet-moon.trycloudflare.com/v1/realtime/client-secret"
        calls = []

        def opener(request, timeout):
            calls.append((request.full_url, timeout))
            if len(calls) == 1:
                raise urllib.error.URLError("dns not propagated")
            raise urllib.error.HTTPError(request.full_url, 401, "Unauthorized", {}, None)

        with mock.patch("aicall_tools.realtime.realtime_offcall_lab.time.sleep", return_value=None):
            wait_for_public_broker(endpoint, timeout_seconds=1, opener=opener)

        self.assertEqual(2, len(calls))
        self.assertEqual(endpoint, calls[0][0])

    def test_public_readiness_waits_for_dns_warmup_before_first_lookup(self):
        from aicall_tools.realtime.realtime_offcall_lab import PUBLIC_DNS_WARMUP_SECONDS, wait_for_public_broker

        endpoint = "https://quiet-moon.trycloudflare.com/v1/realtime/client-secret"
        events = []

        class Response:
            status = 401

            def __enter__(self):
                return self

            def __exit__(self, exc_type, exc, tb):
                return False

        def opener(request, timeout):
            events.append("open")
            return Response()

        def sleeper(seconds):
            events.append(("sleep", seconds))

        wait_for_public_broker(
            endpoint,
            timeout_seconds=1,
            opener=opener,
            sleeper=sleeper,
        )

        self.assertEqual(
            [("sleep", PUBLIC_DNS_WARMUP_SECONDS), "open"],
            events,
        )
        self.assertGreaterEqual(PUBLIC_DNS_WARMUP_SECONDS, 3.0)

    def test_quick_tunnel_retries_fresh_process_after_transient_failure(self):
        from aicall_tools.realtime.realtime_offcall_lab import start_ready_quick_tunnel

        created = []
        public_calls = []

        class FakeProcess:
            def __init__(self):
                self.returncode = None
                self.terminated = False

            def poll(self):
                return self.returncode

            def terminate(self):
                self.terminated = True
                self.returncode = 0

            def wait(self, timeout=None):
                return 0

            def kill(self):
                self.returncode = -9

        def fake_popen(args, **kwargs):
            process = FakeProcess()
            created.append(process)
            return process

        def tunnel_waiter(process):
            index = created.index(process) + 1
            return f"https://attempt-{index}.trycloudflare.com"

        def public_waiter(endpoint):
            public_calls.append(endpoint)
            if len(public_calls) == 1:
                raise TimeoutError("transient Quick Tunnel")

        tunnel, endpoint = start_ready_quick_tunnel(
            cloudflared="/opt/homebrew/bin/cloudflared",
            port=18765,
            environment={"PATH": "/bin", "OPENAI_API_KEY": "must-not-leak"},
            popen=fake_popen,
            tunnel_waiter=tunnel_waiter,
            public_waiter=public_waiter,
            attempts=2,
        )

        self.assertEqual(2, len(created))
        self.assertTrue(created[0].terminated)
        self.assertIs(tunnel, created[1])
        self.assertFalse(created[1].terminated)
        self.assertEqual(
            "https://attempt-2.trycloudflare.com/v1/realtime/client-secret",
            endpoint,
        )
        self.assertNotIn("OPENAI_API_KEY", created[1].__dict__)

    def test_run_lab_never_passes_openai_key_to_tunnel_or_smoke(self):
        from aicall_tools.realtime import realtime_offcall_lab as lab

        api_key = "sk-super-secret-host-only"
        token = "broker-" + "x" * 40
        created = []
        smoke_calls = []
        public_endpoints = []

        class FakeProcess:
            def __init__(self, args, env):
                self.args = list(args)
                self.env = dict(env)
                self.stderr = iter(())
                self.returncode = None
                self.terminated = False

            def poll(self):
                return self.returncode

            def terminate(self):
                self.terminated = True
                self.returncode = 0

            def wait(self, timeout=None):
                self.returncode = 0
                return 0

            def kill(self):
                self.returncode = -9

        def fake_popen(args, **kwargs):
            process = FakeProcess(args, kwargs["env"])
            created.append(process)
            return process

        def fake_run(args, **kwargs):
            self.assertEqual(1, len(public_endpoints))
            smoke_calls.append((list(args), dict(kwargs["env"])))
            return SimpleNamespace(returncode=0)

        with mock.patch.object(lab, "generate_broker_token", return_value=token):
            code = lab.run_lab(
                "RFCT70L7E8J",
                host_env={"OPENAI_API_KEY": api_key, "PATH": "/bin"},
                popen=fake_popen,
                run=fake_run,
                which=lambda name: "/opt/homebrew/bin/cloudflared" if name == "cloudflared" else None,
                port_picker=lambda: 18765,
                broker_waiter=lambda process, port: self.assertEqual(18765, port),
                tunnel_waiter=lambda process: "https://quiet-moon.trycloudflare.com",
                public_waiter=lambda endpoint: public_endpoints.append(endpoint),
            )

        self.assertEqual(0, code)
        self.assertEqual(
            ["https://quiet-moon.trycloudflare.com/v1/realtime/client-secret"],
            public_endpoints,
        )
        self.assertEqual(2, len(created))
        broker, tunnel = created
        self.assertEqual(api_key, broker.env["OPENAI_API_KEY"])
        self.assertEqual(token, broker.env["AI_CALL_BRIDGE_BROKER_TOKEN"])
        self.assertNotIn(api_key, " ".join(broker.args))

        self.assertNotIn("OPENAI_API_KEY", tunnel.env)
        self.assertNotIn(token, " ".join(tunnel.args))

        self.assertEqual(1, len(smoke_calls))
        smoke_args, smoke_env = smoke_calls[0]
        self.assertNotIn("OPENAI_API_KEY", smoke_env)
        self.assertEqual(token, smoke_env["AI_CALL_BRIDGE_BROKER_TOKEN"])
        self.assertEqual(
            "https://quiet-moon.trycloudflare.com/v1/realtime/client-secret",
            smoke_env["AI_CALL_BRIDGE_BROKER_HTTPS_URL"],
        )
        self.assertNotIn(api_key, " ".join(smoke_args))
        self.assertNotIn(token, " ".join(smoke_args))
        self.assertTrue(broker.terminated)
        self.assertTrue(tunnel.terminated)

    def test_cleanup_kills_process_that_does_not_terminate(self):
        from aicall_tools.realtime.realtime_offcall_lab import stop_process

        process = mock.Mock()
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired("p", 5), 0]

        stop_process(process)

        process.terminate.assert_called_once()
        process.kill.assert_called_once()


if __name__ == "__main__":
    unittest.main()
