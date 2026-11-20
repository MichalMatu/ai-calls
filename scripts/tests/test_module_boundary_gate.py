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
    def test_missing_module_rejected(self):
        with tempfile.TemporaryDirectory() as t:
            r=Path(t);mock(r);f=r/"settings.gradle.kts"
            f.write_text(f.read_text().replace('include(":app")',""))
            self.assertIn("module_set_changed",audit(r))
if __name__=="__main__":unittest.main()
