"""Regression of UI screenshot inventory independent from instrumentation stdout."""
import tempfile
import re
import unittest
from pathlib import Path
from zipfile import ZipFile

from aicall_tools.diagnostics.ui_screenshot_inventory import (
    SCREENS, inventory, make_archive, main,
)


def jpeg(path: Path, valid: bool = True) -> None:
    path.write_bytes((b"\xff\xd8" if valid else b"NO") + b"x" * 400 +
                     (b"\xff\xd9" if valid else b"NO"))


class ScreenshotReceiptTest(unittest.TestCase):
    def test_expected_screens_match_current_capture_entries(self):
        source = (Path(__file__).resolve().parents[2] /
                  "app/src/androidTest/kotlin/pl/michalmatu/aicallbridge/AndroidVisualScreenAuditTest.kt").read_text()
        captured = re.findall(r'capture\(\w+::class\.java,\s*"([a-z-]+)"', source)
        self.assertCountEqual(SCREENS, captured)

    def test_full_mixed_one_two_three_page_capture_and_zip(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder) / "ui-audit-ux12-20261108-a"
            root.mkdir()
            for i, label in enumerate(SCREENS):
                for page in range(1 + i % 3):
                    jpeg(root / f"{label}-{page}.jpg")
            result = inventory(root)
            self.assertTrue(result["ok"], result)
            self.assertEqual(len(SCREENS), len(result["screens"]))
            self.assertEqual(sum(1 + i % 3 for i in range(len(SCREENS))), result["total"])
            output = Path(folder) / "private.zip"
            self.assertEqual(0, main([str(root), "--run-id", "ux12-20261108-a", "--archive", str(output)]))
            with ZipFile(output) as archive:
                self.assertEqual(result["total"] + 1, len(archive.namelist()))
                self.assertIn("manifest.json", archive.namelist())
            with self.assertRaises(FileExistsError):
                make_archive(root, output, result)

    def test_missing_screen_is_not_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            jpeg(root / "home-0.jpg")
            self.assertFalse(inventory(root)["ok"])

    def test_no_index_zero_is_not_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            jpeg(root / "home-1.jpg")
            result = inventory(root, ("home",))
            self.assertIn("noncontiguous_pages:home", result["errors"])

    def test_invalid_jpeg_is_not_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            jpeg(root / "home-0.jpg", valid=False)
            self.assertIn("invalid_jpeg:home", inventory(root, ("home",))["errors"])

    def test_unknown_jpeg_is_not_assimilated(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            jpeg(root / "private-0.jpg")
            self.assertIn("unexpected_jpeg", inventory(root, ("home",))["errors"])

    def test_run_id_must_match_new_capture_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual(2, main([folder, "--run-id", "ux12-20261108-a"]))

    def test_empty_directory_is_not_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertFalse(inventory(Path(folder))["ok"])


if __name__ == "__main__":
    unittest.main()
