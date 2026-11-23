"""Fail closed on forbidden Gradle dependencies and Android imports in JVM domain."""
from pathlib import Path
import re

EDGES={
    "app":{"conversation-core","conversation-tools","audio-bridge","privileged-helper","realtime-client"},
    "conversation-core":set(),"conversation-tools":set(),"audio-bridge":set(),
    "privileged-helper":set(),"realtime-client":{"audio-bridge"}
}
PURE={"conversation-core","conversation-tools"}
DEP=re.compile(r'\b(?:findProject|project)\(\s*(?:path\s*=\s*)?["\']:([^"\']+)["\']\s*\)')
INC=re.compile(r'include\(\s*["\']:([^"\']+)["\']\s*\)')
BAD=re.compile(r'^\s*import\s+(?:static\s+)?(?:android\.|androidx\.|'
               r'pl\.michalmatu\.aicallbridge\.(?:ui|debug|developerrelay|session|campaign|'
               r'incoming|textagent|audioengines|identity|localcall|localspeech|tools)\.)')

# The audio engine layer may serve UI presenters but must never depend on them.
# The Android screen owner lives in ui/modelvoice, not in audioengines.
BACKWARD_AUDIO_IMPORT = re.compile(
    r'^\s*import\s+pl\.michalmatu\.aicallbridge\.(?:presentation|ui)\.'
)

# Presentation may depend on domain/data contracts, never on Android widgets or UI.
# Resource identifiers belong to the UI: presentation uses semantic localization keys.
# This gate blocks the UI package and direct app R imports from presentation.
PRESENTATION_REVERSE_IMPORT = re.compile(
    r'^\s*import\s+(?:static\s+)?(?:android\.|androidx\.|'
    r'pl\.michalmatu\.aicallbridge\.(?:ui\.|data\.|R\b))'
)


# Domain is Android-free; data adapters may use Android but cannot import screens.
DOMAIN_REVERSE_IMPORT = re.compile(
    r'^\s*import\s+(?:static\s+)?(?:android\.|androidx\.|'
    r'pl\.michalmatu\.aicallbridge\.(?:ui\.|presentation\.|data\.|R\b))'
)
DATA_REVERSE_IMPORT = re.compile(
    r'^\s*import\s+(?:static\s+)?pl\.michalmatu\.aicallbridge\.'
    r'(?:ui\.|presentation\.|R\b)'
)

def audit(root:Path)->list[str]:
    issues=[]
    settings=root/"settings.gradle.kts"
    if not settings.exists():return ["settings.gradle.kts:missing"]
    declared=set(INC.findall(settings.read_text()))
    if declared!=set(EDGES):issues.append("module_set_changed")
    for module,allowed in EDGES.items():
        build=root/module/"build.gradle.kts"
        if not build.exists():
            issues.append(module+":build_missing");continue
        code=build.read_text()
        for dep in sorted(set(DEP.findall(code))-allowed):
            issues.append(module+":forbidden_dependency:"+dep)
        if module=="app" and "com.android.application" not in code:
            issues.append("app:must_be_application")
        if module not in PURE:continue
        if "org.jetbrains.kotlin.jvm" not in code or "com.android" in code:
            issues.append(module+":must_be_android_free_jvm")
        for p in sorted((root/module/"src/main").rglob("*")):
            if p.suffix not in (".kt",".java"):continue
            for no,line in enumerate(p.read_text(errors="replace").splitlines(),1):
                if BAD.match(line):issues.append(str(p.relative_to(root))+":"+str(no)+":android_or_ui_import")
    audio_root = root / "app/src/main/kotlin/pl/michalmatu/aicallbridge/audioengines"
    if audio_root.exists():
        for p in sorted(audio_root.rglob("*")):
            if p.suffix not in (".kt", ".java"): continue
            for no, line in enumerate(p.read_text(errors="replace").splitlines(), 1):
                if BACKWARD_AUDIO_IMPORT.match(line):
                    issues.append(str(p.relative_to(root)) + ":" + str(no) + ":reverse_ui_dependency")
    text_root = root / "app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent"
    legacy_entrypoint_import = "import pl.michalmatu.aicallbridge.ui.modelvoice.ModelLibraryScreenActivity"
    if text_root.exists():
        for p in sorted(text_root.rglob("*")):
            if p.suffix not in (".kt", ".java"): continue
            for no, line in enumerate(p.read_text(errors="replace").splitlines(), 1):
                if not BACKWARD_AUDIO_IMPORT.match(line): continue
                if p.name == "LanguageModelActivity.kt" and line.strip() == legacy_entrypoint_import:
                    continue
                issues.append(str(p.relative_to(root)) + ":" + str(no) + ":reverse_ui_dependency")
    presentation_root = root / "app/src/main/kotlin/pl/michalmatu/aicallbridge/presentation"
    if presentation_root.exists():
        for p in sorted(presentation_root.rglob("*")):
            if p.suffix not in (".kt", ".java"): continue
            for no, line in enumerate(p.read_text(errors="replace").splitlines(), 1):
                if PRESENTATION_REVERSE_IMPORT.match(line):
                    issues.append(str(p.relative_to(root)) + ":" + str(no) +
                                  ":presentation_reverse_ui_dependency")
    source_root = root / "app/src/main/kotlin/pl/michalmatu/aicallbridge"
    for layer, pattern, issue_name in (
        ("domain", DOMAIN_REVERSE_IMPORT, "domain_reverse_dependency"),
        ("data", DATA_REVERSE_IMPORT, "data_reverse_ui_dependency"),
    ):
        layer_root = source_root / layer
        if not layer_root.exists():
            continue
        for source in sorted(layer_root.rglob("*")):
            if source.suffix not in (".kt", ".java"):
                continue
            for number, line in enumerate(source.read_text(errors="replace").splitlines(), 1):
                if pattern.match(line):
                    issues.append(str(source.relative_to(root)) + ":" +
                                  str(number) + ":" + issue_name)
    return issues

def main()->int:
    issues=audit(Path(__file__).resolve().parents[3])
    for issue in issues:
        print("MODULE_BOUNDARY_ERROR="+issue)
    if issues:return 1
    print("MODULE_BOUNDARY_GATE_PASS=true")
    return 0

if __name__=="__main__":raise SystemExit(main())
