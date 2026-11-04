#!/usr/bin/env python3
"""Host-only M1–M4 architecture/authority regression gate.

This is a structural guard for source contracts, not a claim of native readiness,
Polish ASR accuracy, audible TX, or carrier-side success. It never touches ADB,
a model, the network, or private audio. Do not add runtime product restrictions.
"""
from __future__ import annotations

import re
from pathlib import Path


BASE = "app/src/main/kotlin/pl/michalmatu/aicallbridge/"
AUDIO = BASE + "audioengines/"


def audit(root: Path) -> list[str]:
    errors: list[str] = []

    def read(name: str) -> str:
        path = root / name
        if not path.is_file():
            errors.append(f"{name}: missing required audio engine source")
            return ""
        return path.read_text(encoding="utf-8")

    files = {
        name: read(AUDIO + name + ".kt")
        for name in (
            "AudioEngineProfile", "AudioEngineProfileStore", "AudioEngineReadiness",
            "AudioEngineProviders", "ParakeetSpeechInputProvider",
            "ParakeetModelDownloader", "GemmaAudioDirectInputProvider",
        )
    }
    tts = read(BASE + "localspeech/LocalTtsSpeechOutput.kt")
    settings = read(AUDIO + "AudioEngineSettingsActivity.kt")
    native_tests = {
        name: read(
            "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/audioengines/"
            + name + ".kt"
        )
        for name in (
            "AndroidAudioEngineProfileStoreContractTest",
            "AndroidAudioEngineSettingsActivityTest",
            "AndroidParakeetNativeOffcallTest",
            "AndroidGemmaAudioNativeOffcallTest",
            "AndroidParakeetPolishRoundTripTest",
        )
    }

    def require(file: str, src: str, pattern: str, label: str) -> None:
        if not re.search(pattern, src, re.S):
            errors.append(f"{file}: missing {label}")

    def forbid(file: str, src: str, pattern: str, label: str) -> None:
        if re.search(pattern, src, re.I):
            errors.append(f"{file}: unsafe {label}")

    profile = files["AudioEngineProfile"]
    require("AudioEngineProfile", profile, r"val STABLE\s*=\s*AudioEngineProfile\s*\(\s*\)", "stable-default-no-override")
    for selection in ("ANDROID_STT", "GEMMA_4_E2B", "ANDROID_TTS_PL"):
        require("AudioEngineProfile", profile, r"val \w+\s*:\s*\w+\s*=\s*\w+\." + selection + r"\b",
                "stable baseline " + selection)
    require("AudioEngineProfile", profile, r"GEMMA_AUDIO_DIRECT\s*->\s*\"audio_direct_s22_unqualified\"",
            "experimental-audio-not-selectable")
    require("AudioEngineProfile", profile, r"GEMMA_4_E4B\s*->\s*\"e4b_not_integrated\"",
            "unsupported-E4B-not-selectable")
    require("AudioEngineProfile", profile, r"\"GEMMA_AUDIO_DIRECT\"[\s\S]{0,200}\bfalse\s*\)",
            "M4-gallery-not-ready")
    require("AudioEngineProfileStore", files["AudioEngineProfileStore"],
            r"AudioEngineRegistry\.incompatibility\(it\)\s*==\s*null",
            "unknown-profile-no-silent-fallback")
    require("AudioEngineProfileStore", files["AudioEngineProfileStore"],
            r"getSharedPreferences\(\s*preferencesName", "separate-profile-namespace")

    p = files["ParakeetSpeechInputProvider"]
    require("ParakeetSpeechInputProvider", p, r"const val EXPECTED_SHA256\s*=\s*\"[0-9a-f]{64}\"",
            "pinned-model-sha256")
    require("ParakeetSpeechInputProvider", p, r"const val MIN_SIZE_BYTES\s*=\s*614_588_752L",
            "pinned-model-exact-size")
    require("ParakeetSpeechInputProvider", p, r"validPinnedModel\(model\)", "native-start-model-identity-check")
    require("ParakeetSpeechInputProvider", p, r"ArrayBlockingQueue<ByteArray>", "bounded-nonblocking-pcm-queue")
    require("ParakeetSpeechInputProvider", p, r"run\.queue\.offer\(", "rx-must-not-block")
    require("ParakeetSpeechInputProvider", p, r"fun cancel\(\)", "cancel-port")
    require("ParakeetSpeechInputProvider", p, r"fun finishInput\(\)", "finish-port")

    downloader = files["ParakeetModelDownloader"]
    require("ParakeetModelDownloader", downloader, r"EXPECTED_SHA256", "verify-candidate-hash")
    require("ParakeetModelDownloader", downloader, r"MIN_SIZE_BYTES", "verify-candidate-size")
    require("ParakeetModelDownloader", downloader, r"candidate\.renameTo\(destination\)",
            "staged-model-install")
    require("ParakeetModelDownloader", downloader, r"fun cancel\(", "explicit-cancel")

    provider = files["AudioEngineProviders"]
    require("AudioEngineProviders", provider, r"enginePackage\s*=\s*PiperServiceContract\.PACKAGE",
            "Piper-engine-explicit-binding")
    require("AudioEngineProviders", provider, r"interface SpeechInputProvider", "independent-input-port")
    require("AudioEngineProviders", provider, r"interface SpeechOutputProvider", "independent-output-port")
    require("LocalTtsSpeechOutput", tts, r"TextToSpeech\(appContext, initListener, enginePackage\)",
            "no-default-Piper-engine-fallback")
    require("LocalTtsSpeechOutput", tts, r"\"piperVoice\" in it\.features",
            "native-Piper-voice-marker")
    require("AudioEngineReadiness", files["AudioEngineReadiness"], r"tts_offline_voice_probe_required",
            "no-untested-offline-voice-readiness")
    require("AudioEngineSettingsActivity", settings, r"ParakeetModelDownloader",
            "user-triggered-Parakeet-download")

    m4 = files["GemmaAudioDirectInputProvider"]
    require("GemmaAudioDirectInputProvider", m4, r"Content\.AudioBytes\(", "native-audio-bytes-path")
    require("GemmaAudioDirectInputProvider", m4, r"fun cancel\(", "cancel-port")

    # Scope guard: audio adapters and synthetic tests cannot own Telecom authority,
    # initiate a call, send transcript/audio through HTTP, or emit conversation text.
    for name in (
        "AudioEngineProviders", "ParakeetSpeechInputProvider",
        "GemmaAudioDirectInputProvider",
    ):
        src = files[name]
        forbid(name, src, r"\b(?:TelecomManager|placeCall|ACTION_CALL|startActivityForResult|Shizuku)\b",
               "Telecom-or-privileged-transport-ownership")
        forbid(name, src, r"\b(?:OkHttpClient|HttpURLConnection|Retrofit|WebSocket)\b",
               "network-in-offline-audio-provider")
        forbid(name, src, r"\b(?:Log\.[dievw]|println|printStackTrace)\s*\(",
               "raw-audio-or-transcript-logging")

    for name, src in native_tests.items():
        require(name, src, r"@Test\s+fun\s+\w+\s*\(", "executable-instrumented-test")
        forbid(name, src,
               r"\b(?:TelecomManager|placeCall|ACTION_CALL|ACTION_DIAL|"
               r"CampaignAuthorizationStore|AppOwnedCampaignService|Shizuku)\b",
               "call-or-grant-mutation-in-synthetic-test")
        forbid(name, src, r"\b(?:Log\.[dievw]|printStackTrace)\s*\(",
               "private-audio-test-logging")

    require("AndroidParakeetPolishRoundTripTest", native_tests["AndroidParakeetPolishRoundTripTest"],
            r"ParakeetSpeechInputProvider\(", "real-native-ASR-input")
    require("AndroidParakeetPolishRoundTripTest", native_tests["AndroidParakeetPolishRoundTripTest"],
            r"LocalTtsSpeechOutput\(", "synthetic-offline-Polish-TTS")
    return errors


def main() -> int:
    root = Path(__file__).resolve().parents[3]
    errors = audit(root)
    for item in errors:
        print("AUDIO_ENGINE_QUALITY_REGRESSION: " + item)
    if errors:
        print(f"AUDIO_ENGINE_QUALITY_GATE=FAIL ({len(errors)} structural regressions)")
        return 1
    print("AUDIO_ENGINE_QUALITY_GATE=PASS; source and native-test contracts")
    print("DEVICE_STATUS=NOT_CHECKED; no model inference, ADB, call, or network")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
