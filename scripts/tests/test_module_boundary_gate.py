from pathlib import Path
import tempfile,unittest
from scripts.aicall_tools.diagnostics.module_boundary_gate import EDGES,audit

def mock(root):
    (root/"settings.gradle.kts").write_text("".join('include(":'+m+'")\n' for m in EDGES))
    for m,deps in EDGES.items():
        p=root/m
        (p/"src/main/kotlin").mkdir(parents=True)
        plugin="org.jetbrains.kotlin.jvm" if m in ("conversation-core","conversation-tools") else ("com.android.application" if m=="app" else "com.android.library")
        (p/"build.gradle.kts").write_text('plugins { id("'+plugin+'") }\n'+''.join('implementation(project(":'+d+'"))\n' for d in deps))

class ModuleGateTest(unittest.TestCase):
    def test_clean(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);mock(r);self.assertEqual(audit(r),[])
    def test_core_reverse_dep_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);mock(r);f=r/"conversation-core/build.gradle.kts"
            f.write_text(f.read_text()+'implementation(project(":app"))')
            self.assertIn("conversation-core:forbidden_dependency:app",audit(r))
    def test_named_gradle_project_dependency_is_checked(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            mock(root)
            build = root / "conversation-core/build.gradle.kts"
            with build.open("a") as stream:
                stream.write('testImplementation(project(path = ":app"))\n')
            self.assertIn("conversation-core:forbidden_dependency:app", audit(root))
    def test_android_import_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);mock(r);f=r/"conversation-tools/src/main/kotlin/X.kt"
            f.write_text("import android.content.Context\n")
            self.assertTrue(any("android_or_ui_import" in x for x in audit(r)))
    def test_ui_import_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);mock(r);f=r/"conversation-core/src/main/kotlin/X.kt"
            f.write_text("import pl.michalmatu.aicallbridge.ui.UiDesign\n")
            self.assertTrue(any("android_or_ui_import" in x for x in audit(r)))
    def test_java_static_import_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t)
            mock(root)
            source = root / "conversation-core/src/main/java/Bad.java"
            source.parent.mkdir()
            source.write_text("import static android.os.SystemClock.elapsedRealtime;\n")
            self.assertTrue(any("android_or_ui_import" in issue for issue in audit(root)))
    def test_missing_module_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);mock(r);f=r/"settings.gradle.kts"
            f.write_text(f.read_text().replace('include(":app")',""))
            self.assertIn("module_set_changed",audit(r))
    def test_audio_engine_cannot_import_ui_presentation(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t); mock(root)
            source = root / "app/src/main/kotlin/pl/michalmatu/aicallbridge/audioengines/Bad.kt"
            source.parent.mkdir(parents=True, exist_ok=True)
            for target in (
                "pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceProfileController",
                "pl.michalmatu.aicallbridge.ui.modelvoice.ModelVoiceModelDialog",
            ):
                source.write_text("package pl.michalmatu.aicallbridge.audioengines\nimport " + target + "\n")
                self.assertTrue(any(issue.endswith(":reverse_ui_dependency") for issue in audit(root)))
    def test_ui_screen_can_depend_on_audio_domain(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t); mock(root)
            source = root / "app/src/main/kotlin/pl/michalmatu/aicallbridge/ui/modelvoice/Owner.kt"
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text("import pl.michalmatu.aicallbridge.audioengines.AudioEngineProfile\n")
            self.assertEqual(audit(root), [])
    def test_text_backend_cannot_import_ui_or_presentation(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t); mock(root)
            source = root / "app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/Backend.kt"
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text("import pl.michalmatu.aicallbridge.ui.UiDesign\n")
            self.assertTrue(any(i.endswith(":reverse_ui_dependency") for i in audit(root)))
            source.write_text("import pl.michalmatu.aicallbridge.presentation.modelvoice.ModelVoiceReadinessPresenter\n")
            self.assertTrue(any(i.endswith(":reverse_ui_dependency") for i in audit(root)))

    def test_legacy_manifest_entrypoint_has_one_explicit_ui_import(self):
        with tempfile.TemporaryDirectory() as t:
            root = Path(t); mock(root)
            source = root / "app/src/main/kotlin/pl/michalmatu/aicallbridge/textagent/LanguageModelActivity.kt"
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text("import pl.michalmatu.aicallbridge.ui.modelvoice.ModelLibraryScreenActivity\n")
            self.assertEqual(audit(root), [])
            source.write_text("import pl.michalmatu.aicallbridge.ui.modelvoice.*\n")
            self.assertTrue(any(i.endswith(":reverse_ui_dependency") for i in audit(root)))
if __name__=="__main__":unittest.main()
