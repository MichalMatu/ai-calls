"""Single documentation entrypoint and quick link validation."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from scripts.aicall_tools.diagnostics.docs_check import check

ROOT = Path(__file__).resolve().parents[2]


class DocumentationContractTest(unittest.TestCase):
    def test_current_docs_and_references_are_consistent(self):
        self.assertEqual([], check(ROOT))

    def test_missing_relative_link_fails_closed(self):
        with TemporaryDirectory() as d:
            root = Path(d)
            (root / "README.md").write_text("# Broken\n[link](docs/obsolete.md)\n")
            issues = check(root)
            self.assertTrue(any("broken relative link" in item for item in issues), issues)

    def test_no_chronological_checkpoint_or_old_blockers_on_current_tree(self):
        self.assertFalse((ROOT / "BLOCKERS.md").exists())
        self.assertEqual([], list((ROOT / "docs").glob("checkpoints/*.md")))


if __name__ == "__main__":
    unittest.main()
