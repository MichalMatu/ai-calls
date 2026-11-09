#!/usr/bin/env python3
"""Render local G5.3 synthetic STT/LLM/TTS evidence for independent HUMAN review.

Source stays on the owner's Mac. This script never sends data to a service,
never assigns automatic semantic PASS, and does not print transcript content.
"""
from __future__ import annotations

import argparse
import hashlib
from html import escape
import json
import os
from pathlib import Path
import re
import stat
import wave

DIMENSIONS = (
    "TOPIC", "MEMORY", "CORRECTIONS", "FACTUALITY",
    "NATURALNESS", "QUESTIONS", "PROMISES", "ENDING",
)
SCHEMA = "g53-synthetic-audio-review-v1"
WAV_NAME = re.compile(r"^turn-[0-9]{2}-(?:rx|tx)\.wav$")


def _wav_seconds(path: Path) -> float:
    if path.is_symlink() or not path.is_file():
        raise ValueError("invalid_wav_path")
    with wave.open(str(path), "rb") as source:
        if (source.getnchannels(), source.getsampwidth(), source.getframerate(),
                source.getcomptype()) != (1, 2, 16000, "NONE"):
            raise ValueError("unexpected_pcm_format")
        if source.getnframes() <= 0:
            raise ValueError("empty_wav")
        return source.getnframes() / source.getframerate()


def load(corpus: Path) -> tuple[dict, list[dict]]:
    if corpus.is_symlink() or not corpus.is_dir():
        raise ValueError("corpus_directory_invalid")
    manifest = corpus / "manifest.json"
    if manifest.is_symlink() or not manifest.is_file():
        raise ValueError("manifest_not_complete")
    data = json.loads(manifest.read_text(encoding="utf-8"))
    if (data.get("schema") != SCHEMA or data.get("source") != "synthetic_offcall_test"
            or data.get("no_telecom") is not True
            or data.get("sample_rate_hz") != 16000
            or data.get("pcm_format") != "pcm_s16le_mono"):
        raise ValueError("unsupported_or_untrusted_corpus")
    turns = data.get("turns")
    if not isinstance(turns, list) or not turns:
        raise ValueError("corpus_has_no_turns")
    seen = set()
    for number, turn in enumerate(turns, 1):
        if not isinstance(turn, dict) or turn.get("turn") != number:
            raise ValueError("invalid_turn_number")
        for key in ("reference_utterance", "stt_final", "approved_response"):
            if not isinstance(turn.get(key), str) or not turn[key].strip():
                raise ValueError("missing_review_text")
        for field in ("stt_ms", "approval_ms", "tts_end_ms"):
            if not isinstance(turn.get(field), int) or turn[field] < 0:
                raise ValueError("invalid_turn_timing")
        if not (turn["stt_ms"] <= turn["approval_ms"] <= turn["tts_end_ms"]):
            raise ValueError("non_monotonic_turn_timing")
        for kind in ("rx_wav", "tx_wav"):
            name = turn.get(kind)
            if (not isinstance(name, str) or not WAV_NAME.fullmatch(name)
                    or name != f"turn-{number:02d}-{kind[:2]}.wav" or name in seen):
                raise ValueError("invalid_or_duplicate_wav_name")
            seen.add(name)
            turn[kind + "_seconds"] = round(_wav_seconds(corpus / name), 2)
    return data, turns


def render(corpus: Path) -> dict[str, object]:
    data, turns = load(corpus)
    cards = []
    for item in turns:
        number = item["turn"]
        stt = item["stt_ms"]
        model = item["approval_ms"] - stt
        tts = item["tts_end_ms"] - item["approval_ms"]
        rx, tx = item["rx_wav"], item["tx_wav"]
        cards.append(
            '<section class="turn"><h2>Tura ' + str(number) + '</h2>'
            + '<p><b>Referencja (mowa syntetyczna)</b></p><p class="copy">'
            + escape(item["reference_utterance"]) + '</p>'
            + '<p><b>STT — rzeczywisty transcript</b></p><p class="copy">'
            + escape(item["stt_final"]) + '</p>'
            + '<audio controls preload="none" src="' + escape(rx, quote=True) + '"></audio>'
            + '<p><b>Gemma — zatwierdzona odpowiedź</b></p><p class="copy">'
            + escape(item["approved_response"]) + '</p>'
            + '<audio controls preload="none" src="' + escape(tx, quote=True) + '"></audio>'
            + '<p class="metrics">STT ' + str(stt) + " ms · model i aprobata "
            + str(model) + " ms · TTS " + str(tts) + " ms · razem "
            + str(item["tts_end_ms"]) + " ms · RX "
            + str(item["rx_wav_seconds"]) + " s · TX "
            + str(item["tx_wav_seconds"]) + " s</p></section>"
        )
    manifest_sha256 = hashlib.sha256((corpus / "manifest.json").read_bytes()).hexdigest()
    rubric = []
    for dim in DIMENSIONS:
        rubric.append(
            '<div class="rubric" data-dimension="' + dim + '"><b>' + dim + '</b>'
            + '<select class="verdict"><option>UNASSESSED</option><option>PASS</option>'
            + '<option>FAIL</option></select>'
            + '<select class="turn-id"><option value="">Wybierz turę</option>'
            + "".join('<option value="' + str(t["turn"]) + '">'
                      + str(t["turn"]) + '</option>' for t in turns)
            + '</select><input class="note" placeholder="Dowód i uzasadnienie">'
            + '</div>'
        )
    script = r"""
function saveHumanReview() {
  const verdicts = Array.from(document.querySelectorAll('[data-dimension]')).map(el => ({
    dimension: el.dataset.dimension,
    verdict: el.querySelector('.verdict').value,
    turn: Number(el.querySelector('.turn-id').value) || null,
    note: el.querySelector('.note').value.trim()
  }));
  if (verdicts.some(v => v.verdict === 'UNASSESSED' || !v.turn || !v.note)) {
    alert('Wszystkie osiem ocen wymaga PASS/FAIL, numeru tury i uzasadnienia.');
    return;
  }
  if (!document.getElementById('reviewer-confirmed').checked) {
    alert('Potwierdź osobiste odsłuchanie wszystkich tur.');
    return;
  }
  const payload = {schema:'g53-human-review-v1', source:'human',
    manifest_sha256: MANIFEST_SHA256, reviewer_confirmed_listened: true,
    reviews: verdicts};
  const blob = new Blob([JSON.stringify(payload, null, 2)], {type:'application/json'});
  const link = document.createElement('a');
  const url = URL.createObjectURL(blob);
  link.href = url;
  link.download = 'human-review.json';
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}
"""
    script = "const MANIFEST_SHA256 = '" + manifest_sha256 + "';\n" + script
    html = ('<!doctype html><html lang="pl"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1">'
            '<meta http-equiv="Content-Security-Policy" content="default-src &#39;none&#39;; '
            'media-src &#39;self&#39; file:; style-src &#39;unsafe-inline&#39;; '
            'script-src &#39;unsafe-inline&#39;; connect-src &#39;none&#39;; '
            'base-uri &#39;none&#39;; form-action &#39;none&#39;">'
            '<title>AI Calls — prywatny przegląd G5.3</title>'
            '<style>body{font:16px system-ui;background:#11151c;color:#f1f3f8;'
            'max-width:980px;margin:0 auto;padding:24px;line-height:1.5}'
            '.turn,.rubric{background:#1e2530;border:1px solid #414b5c;'
            'padding:16px;border-radius:12px;margin:15px 0}'
            '.copy{white-space:pre-wrap;overflow-wrap:anywhere}'
            'audio{width:100%;max-width:540px;margin:8px 0 12px}'
            '.metrics,.muted{color:#b9c3d0}'
            '.rubric{display:grid;gap:10px;grid-template-columns:1fr 130px 125px 2fr}'
            'select,input{font:inherit;padding:8px;min-width:0}'
            'button{font:inherit;padding:11px 20px;cursor:pointer}'
            '@media(max-width:750px){.rubric{grid-template-columns:1fr}}</style>'
            '</head><body><h1>AI Calls — G5.3 prywatna ocena rozmowy</h1>'
            '<p class="muted">Syntetyczny rozmówca, prawdziwe STT / E2B / TTS. '
            'Nie jest to połączenie ani dowód słyszalności po drugiej stronie. '
            'Wyniki semantyczne są UNASSESSED do chwili ręcznej oceny. '
            'Ten plik działa lokalnie, bez żądań sieciowych.</p>'
            '<p>Model: ' + escape(str(data.get("model", ""))) +
            ' · STT: ' + escape(str(data.get("stt_provider", ""))) +
            ' · TTS: ' + escape(str(data.get("tts_provider", ""))) + '</p>'
            + "".join(cards) +
            '<h2>Ocena ośmiu wymiarów — człowiek wskazuje turę i powód</h2>'
            + "".join(rubric)
            + '<p><label><input type="checkbox" id="reviewer-confirmed"> '
            'Osobiście przeczytałem i odsłuchałem wszystkie tury tej sesji.</label></p>'
            + '<button onclick="saveHumanReview()">Zapisz pełną ocenę do prywatnego JSON</button>'
            + '<p class="muted">Pobraną ocenę zaimportuj lokalnie komendą '
            '<code>python3 scripts/aicall_tools/diagnostics/g53_human_review_gate.py '
            '--corpus &lt;folder-sesji&gt; --review-input &lt;pobrany-plik.json&gt;</code>. '
            'Skrypt sprawdza SHA manifestu i osiem ocen; nie przesyła żadnych danych do sieci.</p>'
            + '<script>' + script + '</script></body></html>')
    destination = corpus / "review.html"
    destination.write_text(html, encoding="utf-8")
    os.chmod(destination, stat.S_IRUSR | stat.S_IWUSR)
    return {"turns": len(turns), "wav_files": len(turns) * 2,
            "dimensions": len(DIMENSIONS), "review_status": "UNASSESSED"}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--corpus", type=Path, required=True)
    args = parser.parse_args()
    info = render(args.corpus)
    for key, value in info.items():
        print("G53_" + key.upper() + "=" + str(value))
    print("G53_LOCAL_PRIVATE_REVIEW_READY=true")


if __name__ == "__main__":
    main()
