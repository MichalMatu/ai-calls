#!/usr/bin/env python3
"""Offline private call review: chronological STT/Gemma/TTS, RX/ACK/TX audio and outcomes.

Input: an app-owned, user-authorized USB archive already copied to the Mac.
Output: owner-only HTML + WAV files on the same Mac, never sent to network.
The recorded RX is limited to STT capture windows, NOT the whole downlink.
"""
from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
from html import escape
import io
import json
import os
from pathlib import Path
import re
import shutil
import tarfile
import wave

SESSION = re.compile(r"^[0-9]{12,16}-[0-9a-f]{8}$")
AUDIO = re.compile(r"^turn-[0-9]{2,4}-(rx|tx|tx-ack)\.wav$")
ALLOWED = {"timeline.jsonl", "COMPLETE"}
MAX_JSONL_BYTES = 32 * 1024 * 1024
MAX_WAV_BYTES = 128 * 1024 * 1024


def event_caption(event: dict) -> tuple[str, str]:
    kind = event.get("type", "")
    text = str(event.get("text", ""))
    if kind == "COUNTERPART_STT":
        return "Rozmówca / STT", text
    if kind == "AGENT_APPROVED_TEXT":
        return "AI / zatwierdzony tekst do TTS", text
    if kind == "AGENT_ACK_TX":
        return "AI / zapowiedź wysłana do TX", text + " · " + str(event.get("transport_status", ""))
    if kind == "AGENT_DECISION":
        parts = ["Akcja: " + str(event.get("action_id") or "brak"),
                 "Źródło: " + str(event.get("response_source") or "brak")]
        if isinstance(event.get("classifier_wall_ms"), (int, float)):
            parts.append("Gemma: " + str(event["classifier_wall_ms"]) + " ms")
        if event.get("local_error_present"):
            parts.append("Błąd lokalnej klasyfikacji / wykonania")
        return "Gemma / decyzja", " · ".join(parts)
    if kind == "AGENT_FULL_TX":
        return "AI / wysyłka pełnej odpowiedzi", (
            str(event.get("pcm_bytes_accepted", "?")) + "/" +
            str(event.get("pcm_bytes_requested", "?")) + " bajtów · " +
            str(event.get("transport_status", ""))
        )
    if kind == "START":
        return "Start sesji", "Źródło RX: okna STT; źródło TX: zapis uplinku"
    if kind == "END":
        reason = str(event.get("reason_code", "unknown"))
        return "Koniec sesji", "Powód: " + reason
    if kind in ("RX_PCM", "TX_PCM", "TX_ACK_PCM"):
        return {"RX_PCM": "Audio / konsultant RX", "TX_PCM": "Audio / AI TX",
                "TX_ACK_PCM": "Audio / AI zapowiedź"}[kind], (
            str(event.get("pcm_bytes", 0)) + " bajtów" +
            (" · błąd zapisu" if event.get("storage_error") else "")
        )
    return "Zdarzenie: " + str(kind), " · ".join(
        str(k) + "=" + str(v) for k, v in event.items()
        if k not in ("type", "text", "elapsed_ms", "turn")
    )


def session_date(name: str) -> str:
    try:
        return datetime.fromtimestamp(int(name.split("-")[0]) / 1000).strftime("%Y-%m-%d %H:%M:%S")
    except (ValueError, OverflowError, OSError):
        return "Data nieznana"


def safe_member(member: tarfile.TarInfo) -> tuple[str, str] | None:
    if not member.isfile() or member.size < 0:
        return None
    pieces = member.name.split("/")
    if len(pieces) != 3 or pieces[0] != "call-session-evidence" or not SESSION.fullmatch(pieces[1]):
        return None
    if pieces[2] not in ALLOWED and not AUDIO.fullmatch(pieces[2]):
        return None
    if pieces[2] == "timeline.jsonl" and member.size > MAX_JSONL_BYTES:
        raise ValueError("timeline_too_large")
    if AUDIO.fullmatch(pieces[2]) and member.size > MAX_WAV_BYTES:
        raise ValueError("audio_too_large")
    return pieces[1], pieces[2]


def audio_attributes(audio: bytes) -> tuple[bool, str]:
    try:
        with wave.open(io.BytesIO(audio), "rb") as wav:
            good = (wav.getframerate() == 16000 and wav.getnchannels() == 1
                    and wav.getsampwidth() == 2 and wav.getnframes() > 0
                    and wav.getcomptype() == "NONE")
            return good, ("%.2f s" % (wav.getnframes() / wav.getframerate()))
    except (wave.Error, EOFError, ValueError, ZeroDivisionError):
        return False, "uszkodzony WAV"


def _html_event(event: dict, audio_files: dict[str, tuple[str, str]]) -> str:
    caption, detail = event_caption(event)
    elapsed = event.get("elapsed_ms", 0)
    if not isinstance(elapsed, (int, float)):
        elapsed = 0
    number = event.get("turn")
    turn = (" · tura " + str(number)) if isinstance(number, int) else ""
    label = "%.2f s%s" % (max(0, elapsed) / 1000, turn)
    player = ""
    filename = event.get("file")
    if isinstance(filename, str) and filename in audio_files:
        url, duration = audio_files[filename]
        player = '<audio controls preload="none" src="' + escape(url, quote=True) + '"></audio>' + (
            '<small>' + escape(duration) + ' · lokalny WAV, nie dowód odsłuchu po drugiej stronie</small>'
        )
    return ('<article class="event"><div class="stamp">' + escape(label) +
            '</div><div class="content"><strong>' + escape(caption) +
            '</strong><p>' + escape(detail) + '</p>' + player + '</div></article>')


def build(archive: Path, destination: Path) -> dict[str, object]:
    if not archive.is_file() or not archive.name.endswith(".tar.gz"):
        raise ValueError("not_a_private_archive")
    destination.mkdir(parents=True, exist_ok=True, mode=0o700)
    os.chmod(destination, 0o700)
    audio_dir = destination / "audio"
    audio_dir.mkdir(mode=0o700, exist_ok=True)
    os.chmod(audio_dir, 0o700)

    sessions: dict[str, dict[str, tarfile.TarInfo]] = {}
    with tarfile.open(archive, "r:gz") as container:
        for member in container:
            parsed = safe_member(member)
            if parsed is None:
                continue
            ident, filename = parsed
            files = sessions.setdefault(ident, {})
            if filename in files:
                raise ValueError("duplicate_session_member")
            files[filename] = member

        if not sessions:
            raise ValueError("no_recorded_sessions")
        sections = []
        latest = max(sessions)
        latest_audio = 0
        latest_text = 0
        for ordinal, ident in enumerate(sorted(sessions, reverse=True), 1):
            files = sessions[ident]
            events = []
            entry = files.get("timeline.jsonl")
            if entry:
                stream = container.extractfile(entry)
                if stream is not None:
                    for line in stream:
                        if not line.strip():
                            continue
                        try:
                            parsed_event = json.loads(line)
                        except (ValueError, UnicodeDecodeError):
                            parsed_event = {"type": "INVALID_JSONL"}
                        if isinstance(parsed_event, dict):
                            events.append(parsed_event)

            audio_files: dict[str, tuple[str, str]] = {}
            for filename in sorted(files):
                if not AUDIO.fullmatch(filename):
                    continue
                stream = container.extractfile(files[filename])
                if stream is None:
                    continue
                data = stream.read(MAX_WAV_BYTES + 1)
                if len(data) > MAX_WAV_BYTES:
                    raise ValueError("audio_too_large")
                valid, duration = audio_attributes(data)
                if not valid:
                    continue
                rel = "audio/session-%03d-%s" % (ordinal, filename)
                out = destination / rel
                with out.open("wb") as handle:
                    handle.write(data)
                os.chmod(out, 0o600)
                audio_files[filename] = (rel, duration)

            counts = Counter(str(ev.get("type", "")) for ev in events)
            events = sorted(enumerate(events), key=lambda p: (
                p[1].get("elapsed_ms", 0) if isinstance(p[1].get("elapsed_ms", 0), (int, float)) else 0,
                p[0],
            ))
            rendered = "".join(_html_event(ev, audio_files) for _, ev in events)
            if not rendered:
                rendered = '<p>Brak zdarzeń — sprawdź zawartość archiwum.</p>'
            completeness = "zakończona" if "COMPLETE" in files else "trwa / niekompletna"
            if ident == latest:
                latest_audio = len(audio_files)
                latest_text = counts["COUNTERPART_STT"] + counts["AGENT_APPROVED_TEXT"]
            sections.append(
                '<details ' + ('open' if ordinal == 1 else '') + '><summary><span>Rozmowa ' +
                str(ordinal) + ' · ' + escape(session_date(ident)) + '</span><small>' +
                escape(completeness) + ' · STT: ' + str(counts["COUNTERPART_STT"]) +
                ' · AI: ' + str(counts["AGENT_APPROVED_TEXT"]) + ' · WAV: ' +
                str(len(audio_files)) + '</small></summary><div class="timeline">' +
                rendered + '</div></details>'
            )
    style = """
    :root{color-scheme:dark;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif}
    *{box-sizing:border-box}body{margin:0;background:#101216;color:#f0f2f4;font-size:15px}
    main{max-width:1040px;margin:0 auto;padding:35px 18px 60px}
    h1{font-size:30px;letter-spacing:-.04em;margin-bottom:9px}h2{font-size:16px}
    .sub{color:#aab0ba;max-width:800px;line-height:1.6}
    .stats{display:flex;gap:12px;flex-wrap:wrap;margin:24px 0}
    .stat{background:#20252c;padding:17px 22px;border-radius:14px;min-width:150px}
    .stat b{display:block;font-size:26px}.stat small{color:#aab0ba}
    details{border:1px solid #343b45;border-radius:12px;margin:12px 0;overflow:hidden}
    summary{list-style:none;cursor:pointer;padding:19px;display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;background:#1d2128}
    summary::-webkit-details-marker{display:none}summary span{font-weight:650}
    summary small{color:#acb3be}.timeline{padding:8px 16px 22px}
    .event{display:grid;grid-template-columns:135px 1fr;gap:12px;padding:15px 0;border-bottom:1px solid #303640}
    .event:last-child{border:0}.stamp{color:#aab4c1;font-variant-numeric:tabular-nums;font-size:13px}
    .content strong{font-size:14px}.content p{margin:7px 0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.55}
    .content small{display:block;margin-top:4px;color:#aeb6c0}
    audio{display:block;width:min(100%,430px);margin:11px 0 4px}
    footer{margin-top:28px;color:#aab0ba;line-height:1.6}
    @media(max-width:560px){.event{grid-template-columns:1fr;gap:5px}h1{font-size:25px}}
    """
    html = ('<!doctype html><html lang="pl"><head><meta charset="utf-8">' +
            '<meta name="viewport" content="width=device-width,initial-scale=1">' +
            '<meta http-equiv="Content-Security-Policy" content="default-src &#39;none&#39;; ' +
            'media-src &#39;self&#39; file:; style-src &#39;unsafe-inline&#39;; base-uri &#39;none&#39;">' +
            '<title>AI Calls · prywatny przegląd rozmów</title><style>' + style +
            '</style></head><body><main><h1>AI Calls · obserwowalność rozmów</h1>' +
            '<p class="sub">Lokalny zapis: kto, co, kiedy i jak odpowiedział. ' +
            'Audio RX obejmuje wyłącznie okna przechwycone przez STT. ' +
            'Zapis TX potwierdza lokalny uplink, nie słyszalność po stronie konsultanta. ' +
            'Nie udostępniaj tego folderu — zawiera prywatne rozmowy.</p>' +
            '<section class="stats"><div class="stat"><b>' + str(len(sessions)) +
            '</b><small>sesji</small></div><div class="stat"><b>' + str(latest_text) +
            '</b><small>tekstów w ostatniej</small></div><div class="stat"><b>' +
            str(latest_audio) + '</b><small>WAV w ostatniej</small></div></section>' +
            "".join(sections) +
            '<footer>Raport offline. Żadne dane nie są wysyłane do sieci. ' +
            'Nie zawiera wewnętrznego toku rozumowania modelu; pokazuje jawne akcje i metryki. ' +
            'Starsze sesje mają tylko zdarzenia zapisane przez ówczesną wersję aplikacji.</footer>' +
            '</main></body></html>')
    output = destination / "index.html"
    output.write_text(html, encoding="utf-8")
    os.chmod(output, 0o600)
    return {"sessions": len(sessions), "latest_text_events": latest_text,
            "latest_audio_files": latest_audio, "viewer_path": str(output)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path)
    parser.add_argument("--destination", type=Path)
    args = parser.parse_args()
    private = Path.home() / "Documents/AI-Calls/private-exports"
    choices = sorted(private.glob("call-evidence-*.tar.gz"), key=lambda p: p.stat().st_mtime)
    archive = args.archive or (choices[-1] if choices else None)
    if archive is None:
        parser.error("No private call archive; export it first on the authorized Mac")
    destination = args.destination or (private / ("review-" + archive.name.removeprefix("call-evidence-").removesuffix(".tar.gz")))
    info = build(archive, destination)
    for key, value in info.items():
        print(key.upper() + "=" + str(value))
    print("ALL_PRIVATE_LOCAL_ONLY=true")


if __name__ == "__main__":
    main()
