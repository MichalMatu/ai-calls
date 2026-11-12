"""Mutation checks: the off-call audio quality gate must catch actual unsafe drift.

These tests only create temporary source fixtures: no Android SDK, ADB, model or network.
"""
from pathlib import Path
import shutil
import tempfile
import unittest

from aicall_tools.diagnostics.audit_audio_engine_quality import AUDIO, BASE, audit


ROOT = Path(__file__).resolve().parents[2]
MAIN = "app/src/main/kotlin/pl/michalmatu/aicallbridge/"
TEST = "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/audioengines/"
COPY = [
    AUDIO + name + ".kt" for name in (
        "AudioEngineProfile", "AudioEngineProfileStore", "AudioEngineReadiness", "DialogueEngineProviders",
        "AudioEngineProviders", "ParakeetSpeechInputProvider",
        "ParakeetModelDownloader", "ParakeetModelTransferService",
        "GemmaAudioDirectInputProvider", "AudioEngineSettingsActivity", "ModelVoiceSettings",
    )
] + [MAIN + "localspeech/LocalTtsSpeechOutput.kt",
     MAIN + "conversation/ConversationInputRoutingBackend.kt",
     MAIN + "campaign/AppointmentMvpConversationScenario.kt",
     MAIN + "campaign/ScenarioOutgoingConversationScenario.kt",
     MAIN + "textagent/LanguageModelActivity.kt", MAIN + "textagent/Gemma4LiteRtTextBackend.kt"] + [
    TEST + name + ".kt" for name in (
        "AndroidAudioEngineProfileStoreContractTest",
        "AndroidModelVoiceSettingsTest",
        "AndroidParakeetNativeOffcallTest",
        "AndroidGemmaAudioNativeOffcallTest",
        "AndroidParakeetPolishRoundTripTest",
        "AndroidPolishAudioQualityTest",
    )
]


class AudioEngineQualityGateTest(unittest.TestCase):
    def test_current_source_passes_structural_audio_gate(self):
        self.assertEqual(audit(ROOT), [])

    def test_outgoing_scenario_must_forward_audio_as_well_as_text(self):
        self.check_mutation(
            MAIN + "campaign/ScenarioOutgoingConversationScenario.kt",
            "ConversationInputRoutingBackend(", "TextOnlyWrapper(",
            "outgoing-audio-input-routing",
        )

    def check_mutation(self, path: str, original: str, replacement: str, expected: str):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            for name in COPY:
                dst = root / name
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(ROOT / name, dst)
            target = root / path
            before = target.read_text(encoding="utf-8")
            self.assertIn(original, before, f"fixture token missing in {path}")
            target.write_text(before.replace(original, replacement, 1), encoding="utf-8")
            errors = audit(root)
            self.assertTrue(
                any(expected in err for err in errors),
                f"mutated source passed unexpectedly; errors={errors}",
            )

    def test_parakeet_download_ui_must_use_foreground_service(self):
        self.check_mutation(
            MAIN + "textagent/LanguageModelActivity.kt",
            "startForegroundService(Intent(this, ParakeetModelTransferService::class.java)",
            "startService(Intent(this, ParakeetModelTransferService::class.java)",
            "user-triggered-Parakeet-download",
        )

    def test_audio_must_require_a_native_device_check(self):
        self.check_mutation(
            AUDIO + "AudioEngineReadiness.kt",
            '!ModelCompatibilityStore(context).verified(file, true, profile)',
            'false',
            "audio-requires-native-device-check",
        )

    def test_consumer_dialogue_choices_preserve_the_protected_baseline(self):
        self.check_mutation(
            AUDIO + "AudioEngineProfile.kt",
            'DialogueModelId.GEMMA_4_E2B.name',
            'DialogueModelId.GEMMA_4_E4B.name',
            "baseline-dialogue-choice",
        )

    def test_consumer_dialogue_choices_preserve_explicit_owner_imports(self):
        self.check_mutation(
            AUDIO + "AudioEngineProfile.kt",
            'DialogueModelId.USER_LITERT.name',
            'DialogueModelId.GEMMA_4_E4B.name',
            "owner-file-dialogue-choice",
        )

    def test_retired_catalog_entries_cannot_reappear_as_consumer_choices(self):
        self.check_mutation(
            AUDIO + "AudioEngineProfile.kt",
            'val dialogues = listOf(',
            'val dialogues = listOf(\n        AudioEngineChoice("QWEN_3_0_6B", "retired", "pl-PL", "", true),',
            "retired-consumer-dialogue-choice",
        )

    def test_selected_model_cannot_be_dropped_at_native_factory_boundary(self):
        self.check_mutation(
            AUDIO + "DialogueEngineProviders.kt",
            "context, systemInstruction, allowedActions, id",
            "context, systemInstruction, allowedActions",
            "selected-model-reaches-native-classifier",
        )

    def test_parakeet_native_hash_guard_cannot_be_removed(self):
        self.check_mutation(
            AUDIO + "ParakeetSpeechInputProvider.kt",
            "ParakeetModelFile.validPinnedModel(model)",
            "model.isFile",
            "native-start-model-identity-check",
        )

    def test_pcm_rx_must_not_switch_to_blocking_put(self):
        self.check_mutation(
            AUDIO + "ParakeetSpeechInputProvider.kt",
            "run.queue.offer(",
            "run.queue.put(",
            "rx-must-not-block",
        )

    def test_audio_provider_cannot_own_cellular_calls(self):
        self.check_mutation(
            AUDIO + "GemmaAudioDirectInputProvider.kt",
            "private val lock = Any()",
            "private val lock = Any()\n    val api = TelecomManager()",
            "Telecom-or-privileged-transport-ownership",
        )

    def test_audio_provider_cannot_write_transcripts_to_logs(self):
        self.check_mutation(
            AUDIO + "ParakeetSpeechInputProvider.kt",
            "private val appContext = context.applicationContext",
            'private val appContext = context.applicationContext\n    fun dump(text: String) = println(text)',
            "raw-audio-or-transcript-logging",
        )

    def test_audio_provider_cannot_import_privileged_helper(self):
        self.check_mutation(
            AUDIO + "ParakeetSpeechInputProvider.kt",
            "import android.content.Context",
            "import android.content.Context\nimport rikka.shizuku.Shizuku",
            "Telecom-or-privileged-transport-ownership",
        )

    def test_piper_must_stay_engine_pinned(self):
        self.check_mutation(
            AUDIO + "AudioEngineProviders.kt",
            "enginePackage = PiperServiceContract.PACKAGE",
            "enginePackage = null",
            "Piper-engine-explicit-binding",
        )

    def test_native_offcall_test_cannot_start_calls(self):
        self.check_mutation(
            TEST + "AndroidParakeetPolishRoundTripTest.kt",
            "class AndroidParakeetPolishRoundTripTest {",
            "class AndroidParakeetPolishRoundTripTest {\n    fun startCall() = placeCall()",
            "call-or-grant-mutation-in-synthetic-test",
        )


if __name__ == "__main__":
    unittest.main()
