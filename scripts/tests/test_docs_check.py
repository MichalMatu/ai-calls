"""Documentation quality contract: budgets, links, duplicates and no history."""
from pathlib import Path
from shutil import copytree, copyfile
from tempfile import TemporaryDirectory
import unittest

from scripts.aicall_tools.diagnostics.docs_check import (
    TOTAL_MAX_BYTES, TOTAL_WARN_BYTES, audit, check,
)

ROOT = Path(__file__).resolve().parents[2]


class DocumentationContractTest(unittest.TestCase):
    @staticmethod
    def fixture(root: Path) -> None:
        copytree(ROOT / "docs", root / "docs")
        for name in ("README.md", "AGENTS.md"):
            copyfile(ROOT / name, root / name)

    def test_current_docs_and_references_are_consistent(self):
        errors, warnings, total = audit(ROOT)
        self.assertEqual([], errors)
        self.assertLessEqual(total, TOTAL_MAX_BYTES, warnings)

    def test_missing_relative_code_link_fails_closed(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            (root / "docs/modules/UI.md").write_text(
                "# UI\n\n[missing source](../../app/src/main/Missing.kt)\n"
            )
            self.assertTrue(any("broken relative link" in e for e in check(root)))

    def test_unregistered_document_and_missing_index_are_rejected(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            (root / "docs/obsolete.md").write_text("# Legacy\n")
            index = root / "docs/README.md"
            index.write_text(index.read_text().replace("(modules/UI.md)", "(modules/missing.md)"))
            errors = check(root)
            self.assertTrue(any("unregistered documentation" in e for e in errors))
            self.assertTrue(any("missing index entry for docs/modules/UI.md" in e for e in errors))

    def test_per_file_budget_rejects_bloat(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            path = root / "docs/modules/UI.md"
            path.write_text(path.read_text() + "\n" + "More content " * 1000)
            self.assertTrue(any("file budget exceeded" in e for e in check(root)))

    def test_total_budget_rejects_bloat(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            for path in (root / "docs/modules").glob("*.md"):
                path.write_text(path.read_text() + "\n" + "words " * 950)
            errors, _, total = audit(root)
            self.assertGreater(total, TOTAL_MAX_BYTES)
            self.assertTrue(any("documentation budget exceeded" in e for e in errors))

    def test_total_budget_warns_before_blocking(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            # Grow only to the warning zone, independent of the live docs baseline.
            for path in (root / "docs").rglob("*.md"):
                if path.name != "README.md":
                    path.write_text("# Budget fixture " + path.stem + "\n")
            baseline = audit(root)[2]
            desired = TOTAL_WARN_BYTES + 1024
            self.assertLess(baseline, TOTAL_WARN_BYTES)
            files = sorted((root / "docs").rglob("*.md"))
            per_file, leftover = divmod(desired - baseline, len(files))
            for i, path in enumerate(files):
                count = per_file + (i < leftover)
                path.write_bytes(path.read_bytes() + b"\n" + b"x" * (count - 1))
            errors, warnings, total = audit(root)
            self.assertEqual(desired, total)
            self.assertFalse(any("budget exceeded" in item for item in errors), errors)
            self.assertLess(total, TOTAL_MAX_BYTES)
            self.assertTrue(any("documentation budget nearing limit" in w for w in warnings))

    def test_nearly_identical_long_paragraphs_are_rejected(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            paragraph = "Authorized actions always require the exact session and active scope; " * 6
            first = root / "docs/modules/UI.md"
            second = root / "docs/modules/CONVERSATION.md"
            first.write_text(first.read_text() + "\n\n" + paragraph + "\n")
            second.write_text(second.read_text() + "\n\n"
                              + paragraph.replace("active scope", "valid scope", 1) + "\n")
            self.assertTrue(any("duplicate prose paragraph" in e for e in check(root)))

    def test_dated_heading_outside_project_and_duplicate_heading_are_rejected(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            file = root / "docs/modules/UI.md"
            file.write_text(file.read_text() + "\n## 2026-11-09 — old sprint\n"
                            + "## Otwarte — status\n## Repeated\n## Repeated\n")
            errors = check(root)
            self.assertTrue(any("chronological/archive heading" in e for e in errors))
            self.assertTrue(any("status belongs only" in e for e in errors))
            self.assertTrue(any("duplicate section heading" in e for e in errors))

    def test_single_h1_and_root_policy_are_enforced(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            self.fixture(root)
            file = root / "docs/modules/UI.md"
            file.write_text("# One\n# Two\n")
            (root / "BLOCKERS.md").write_text("# Legacy\n")
            errors = check(root)
            self.assertTrue(any("exactly one top-level heading" in e for e in errors))
            self.assertTrue(any("legacy ledger" in e for e in errors))

    def test_no_historical_checkpoint_or_old_blockers_on_current_tree(self):
        self.assertFalse((ROOT / "BLOCKERS.md").exists())
        self.assertEqual([], list((ROOT / "docs").glob("checkpoints/*.md")))


if __name__ == "__main__":
    unittest.main()
