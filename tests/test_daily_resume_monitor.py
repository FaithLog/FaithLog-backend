import os
import pathlib
import subprocess
import sys
import tempfile
import unittest


REPO_ROOT = pathlib.Path(__file__).resolve().parents[1]
SCRIPT = REPO_ROOT / "scripts/daily_resume_monitor.py"


def run(args, cwd, env=None):
    completed = subprocess.run(
        args,
        cwd=cwd,
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    return completed.returncode, completed.stdout


class DailyResumeMonitorTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.tmp.name)
        (self.root / "docs/prompts").mkdir(parents=True)
        (self.root / "docs").mkdir(exist_ok=True)
        (self.root / "docs/prompts/daily-resume-monitor.md").write_text(
            "# Prompt\n\nOnly verified evidence.\n", encoding="utf-8"
        )
        (self.root / "docs/resume-metrics.md").write_text("# Metrics\n", encoding="utf-8")
        (self.root / "docs/decision-log.md").write_text("# Decisions\n", encoding="utf-8")
        (self.root / "build/test-results/test").mkdir(parents=True)
        (self.root / "build/test-results/test/TEST-example.xml").write_text(
            '<testsuite tests="2" failures="0" errors="0" skipped="0"></testsuite>',
            encoding="utf-8",
        )
        run(["git", "init"], self.root)
        run(["git", "config", "user.email", "test@example.com"], self.root)
        run(["git", "config", "user.name", "Test User"], self.root)
        (self.root / "build.gradle.kts").write_text("plugins { java }\n", encoding="utf-8")
        env = os.environ.copy()
        env["GIT_AUTHOR_DATE"] = "2026-06-16T12:00:00+0900"
        env["GIT_COMMITTER_DATE"] = "2026-06-16T12:00:00+0900"
        run(["git", "add", "."], self.root, env=env)
        code, output = run(["git", "commit", "-m", "docs: monitor evidence"], self.root, env=env)
        self.assertEqual(code, 0, output)

    def tearDown(self):
        self.tmp.cleanup()

    def test_monitor_writes_project_and_obsidian_notes_from_verified_evidence(self):
        obsidian = self.root / "obsidian/FaithLog"
        code, output = run(
            [
                sys.executable,
                str(SCRIPT),
                "--root",
                str(self.root),
                "--obsidian-root",
                str(obsidian),
                "--today",
                "2026-06-17",
            ],
            self.root,
        )

        self.assertEqual(code, 0, output)
        daily_note = self.root / "docs/wiki/daily/2026-06-16.md"
        obsidian_daily_note = obsidian / "wiki/daily/2026-06-16.md"
        self.assertTrue(daily_note.exists())
        self.assertTrue(obsidian_daily_note.exists())
        text = daily_note.read_text(encoding="utf-8")
        self.assertIn("Prompt: `docs/prompts/daily-resume-monitor.md`", text)
        self.assertIn("docs: monitor evidence", text)
        self.assertIn("tests=2, passed=2", text)
        self.assertIn("Transcript source unknown", text)
        decisions = (self.root / "docs/decision-log.md").read_text(encoding="utf-8")
        self.assertIn("Daily Resume Monitor Transcript Source", decisions)

    def test_monitor_fails_when_versioned_prompt_is_missing(self):
        (self.root / "docs/prompts/daily-resume-monitor.md").unlink()
        code, output = run(
            [
                sys.executable,
                str(SCRIPT),
                "--root",
                str(self.root),
                "--obsidian-root",
                str(self.root / "obsidian/FaithLog"),
                "--today",
                "2026-06-17",
            ],
            self.root,
        )

        self.assertNotEqual(code, 0)
        self.assertIn("Daily resume monitor prompt is missing", output)


if __name__ == "__main__":
    unittest.main()
