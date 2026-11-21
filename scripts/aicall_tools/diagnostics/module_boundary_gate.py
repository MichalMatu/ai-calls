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
    return issues

def main()->int:
    issues=audit(Path(__file__).resolve().parents[3])
    for issue in issues:
        print("MODULE_BOUNDARY_ERROR="+issue)
    if issues:return 1
    print("MODULE_BOUNDARY_GATE_PASS=true")
    return 0

if __name__=="__main__":raise SystemExit(main())
