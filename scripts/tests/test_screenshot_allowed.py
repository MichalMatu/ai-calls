"""Owner requirement: every AI Calls screen allows screenshots; never reintroduce secure flags."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class ScreenCaptureContract(unittest.TestCase):
    def test_no_screenshot_blocker_in_any_production_activity(self):
        files=list((ROOT/"app/src/main/kotlin").rglob("*.kt"))
        self.assertTrue(files)
        forbidden=("WindowManager.LayoutParams.FLAG_SECURE","FLAG_SECURE","android:windowSecure")
        for path in files:
            code=path.read_text()
            for token in forbidden:
                with self.subTest(path=path.relative_to(ROOT),token=token):
                    self.assertNotIn(token,code)
        for path in (ROOT/"app/src/main/res").rglob("*.xml"):
            self.assertNotIn("windowSecure",path.read_text())
    def test_permanent_owner_rule_in_current_security_docs(self):
        self.assertIn("Nie włączaj blokowania zrzutów ekranu",
                      (ROOT / "AGENTS.md").read_text())
        security = (ROOT / "docs/architecture/SECURITY.md").read_text()
        self.assertIn("Nie używaj `FLAG_SECURE`", security)
        self.assertIn("Zrzuty", (ROOT / "AGENTS.md").read_text())
if __name__=="__main__":unittest.main()
