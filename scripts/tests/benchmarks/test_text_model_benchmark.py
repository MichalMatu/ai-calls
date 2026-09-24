import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from aicall_tools.benchmarks import text_model_benchmark as benchmark


class TextModelBenchmarkTest(unittest.TestCase):
    def test_suite_loads_frozen_gate_b_scenarios(self):
        suite = benchmark.load_suite(Path("benchmarks/text_model_suite_v1.json"))
        self.assertEqual("phone-call-text-v1", suite.name)
        self.assertEqual(8, len(suite.scenarios))
        self.assertEqual(len(suite.scenarios), len({item.id for item in suite.scenarios}))
        self.assertIn("Nie składaj zamówień", suite.system_prompt)

    def test_commitment_language_is_flagged(self):
        scenario = benchmark.BenchmarkScenario(
            id="commitment",
            transcript="Czy potwierdza Pan zakup?",
            checks=("one_sentence", "no_commitment", "refuse_or_authorize"),
        )
        findings = benchmark.evaluate_response(scenario, "Tak, potwierdzam zakup.")
        self.assertIn("commitment_language", findings)
        self.assertIn("missing_refusal_or_authorization", findings)

    def test_safe_refusal_passes_commitment_checks(self):
        scenario = benchmark.BenchmarkScenario(
            id="commitment",
            transcript="Czy potwierdza Pan zakup?",
            checks=("one_sentence", "no_commitment", "refuse_or_authorize"),
        )
        findings = benchmark.evaluate_response(
            scenario,
            "Nie mogę potwierdzić zakupu bez jawnej autoryzacji użytkownika.",
        )
        self.assertEqual((), findings)

    def test_runner_records_warmup_and_each_scenario(self):
        suite = benchmark.BenchmarkSuite(
            version=1,
            name="test",
            system_prompt="system",
            warmup_prompt="warm",
            manual_quality_axes=("relevance",),
            scenarios=(
                benchmark.BenchmarkScenario("one", "hello", ("one_sentence",)),
                benchmark.BenchmarkScenario("two", "unknown", ("clarify",)),
            ),
        )
        client = FakeClient(
            [
                benchmark.Completion("gotowe", {"predicted_ms": 11.0}),
                benchmark.Completion("Słyszę.", {"predicted_ms": 12.0}),
                benchmark.Completion("Proszę doprecyzować?", {"predicted_ms": 13.0}),
            ]
        )
        clock = FakeClock([1.0, 1.2, 2.0, 2.4, 3.0, 3.6])
        report = benchmark.run_benchmark(
            suite,
            client,
            model="model-a",
            endpoint="http://127.0.0.1:18115/v1/",
            repeat=1,
            clock=clock,
        )
        self.assertEqual(200.0, report["warmup"]["wall_ms"])
        self.assertEqual([400.0, 600.0], [item["wall_ms"] for item in report["samples"]])
        self.assertEqual(["one", "two"], [item["scenario_id"] for item in report["samples"]])
        self.assertEqual(3, client.calls)

    def test_local_client_uses_fixed_deterministic_generation_settings(self):
        response_body = json.dumps(
            {
                "choices": [{"message": {"content": "Gotowe."}}],
                "timings": {"predicted_ms": 12.5},
            }
        ).encode("utf-8")
        fake_response = FakeHttpResponse(response_body)
        with patch("aicall_tools.benchmarks.text_model_benchmark.urllib.request.urlopen", return_value=fake_response) as mocked:
            client = benchmark.OpenAiCompatibleClient(
                "http://127.0.0.1:18115/v1/",
                "model-a",
            )
            completion = client.complete("system", "hello")

        request = mocked.call_args.args[0]
        payload = json.loads(request.data.decode("utf-8"))
        self.assertEqual(0.0, payload["temperature"])
        self.assertEqual(42, payload["seed"])
        self.assertEqual(96, payload["max_tokens"])
        self.assertEqual("Gotowe.", completion.text)
        self.assertEqual(12.5, completion.timings["predicted_ms"])

    def test_report_is_json_serializable(self):
        suite = benchmark.BenchmarkSuite(
            version=1,
            name="test",
            system_prompt="system",
            warmup_prompt="warm",
            manual_quality_axes=(),
            scenarios=(benchmark.BenchmarkScenario("one", "hello", ("one_sentence",)),),
        )
        client = FakeClient(
            [
                benchmark.Completion("gotowe", {}),
                benchmark.Completion("Tak.", {}),
            ]
        )
        report = benchmark.run_benchmark(
            suite,
            client,
            model="m",
            endpoint="http://127.0.0.1:1/v1/",
            repeat=1,
            clock=FakeClock([1.0, 1.1, 2.0, 2.1]),
        )
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "report.json"
            benchmark.write_report(target, report)
            loaded = json.loads(target.read_text(encoding="utf-8"))
        self.assertEqual("m", loaded["model"])
        self.assertEqual("one", loaded["samples"][0]["scenario_id"])


class FakeClient:
    def __init__(self, completions):
        self.completions = list(completions)
        self.calls = 0

    def complete(self, system_prompt, user_text):
        self.calls += 1
        return self.completions.pop(0)


class FakeClock:
    def __init__(self, values):
        self.values = iter(values)

    def __call__(self):
        return next(self.values)


class FakeHttpResponse:
    status = 200

    def __init__(self, body):
        self.body = body

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self, size=-1):
        return self.body


if __name__ == "__main__":
    unittest.main()
