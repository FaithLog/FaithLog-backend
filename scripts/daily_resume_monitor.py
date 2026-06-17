#!/usr/bin/env python3
"""Generate evidence-backed daily resume notes for FaithLog.

This script is intentionally conservative: it records verified local evidence,
adds pending questions for unknowns, and does not schedule itself.
"""

from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import pathlib
import re
import subprocess
import sys
import textwrap
import xml.etree.ElementTree as ET


DEFAULT_OBSIDIAN_ROOT = pathlib.Path(
    "/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog"
)
PROMPT_PATH = pathlib.Path("docs/prompts/daily-resume-monitor.md")
PENDING_TRANSCRIPT_TITLE = "Daily Resume Monitor Transcript Source"
GENERATED_BY = "daily-resume-monitor"


@dataclasses.dataclass(frozen=True)
class GitCommit:
    sha: str
    subject: str
    author_date: str


@dataclasses.dataclass(frozen=True)
class TestResult:
    path: str
    tests: int
    failures: int
    errors: int
    skipped: int

    @property
    def passed(self) -> int:
        return self.tests - self.failures - self.errors - self.skipped


@dataclasses.dataclass(frozen=True)
class Evidence:
    target_date: dt.date
    prompt_chars: int
    agent_rule_files: list[str]
    git_status: list[str]
    commits: list[GitCommit]
    changed_files: list[str]
    dependency_config_changes: list[str]
    migration_changes: list[str]
    test_results: list[TestResult]
    build_artifacts: list[str]
    operations_signals: list[str]
    existing_docs: list[str]
    transcript_source: str | None


def run_command(root: pathlib.Path, args: list[str]) -> tuple[int, str]:
    completed = subprocess.run(
        args,
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    return completed.returncode, completed.stdout.strip()


def read_required_prompt(root: pathlib.Path) -> str:
    prompt_file = root / PROMPT_PATH
    if not prompt_file.exists():
        raise FileNotFoundError(f"Daily resume monitor prompt is missing: {prompt_file}")
    return prompt_file.read_text(encoding="utf-8")


def read_agent_rule_files(root: pathlib.Path) -> list[str]:
    evidence: list[str] = []
    for name in ["AGENTS.md", "AGENT.md"]:
        path = root / name
        if path.exists():
            content = path.read_text(encoding="utf-8")
            evidence.append(f"`{name}` read ({len(content)} characters)")
        else:
            evidence.append(f"`{name}` not present")
    return evidence


def previous_day(today: dt.date | None = None) -> dt.date:
    return (today or dt.date.today()) - dt.timedelta(days=1)


def git_status(root: pathlib.Path) -> list[str]:
    code, output = run_command(root, ["git", "status", "--short", "--branch"])
    if code != 0:
        return [f"git status unavailable: {output}"]
    return output.splitlines()


def commits_for_date(root: pathlib.Path, target_date: dt.date) -> list[GitCommit]:
    since = f"{target_date.isoformat()}T00:00:00"
    until = f"{(target_date + dt.timedelta(days=1)).isoformat()}T00:00:00"
    code, output = run_command(
        root,
        [
            "git",
            "log",
            f"--since={since}",
            f"--until={until}",
            "--date=short",
            "--pretty=format:%h%x1f%ad%x1f%s",
        ],
    )
    if code != 0 or not output:
        return []
    commits: list[GitCommit] = []
    for line in output.splitlines():
        parts = line.split("\x1f")
        if len(parts) == 3:
            commits.append(GitCommit(sha=parts[0], author_date=parts[1], subject=parts[2]))
    return commits


def changed_files_for_commits(root: pathlib.Path, commits: list[GitCommit]) -> list[str]:
    if not commits:
        return []
    files: set[str] = set()
    for commit in commits:
        code, output = run_command(
            root,
            ["git", "show", "--pretty=format:", "--name-only", "--no-renames", commit.sha],
        )
        if code == 0:
            files.update(line for line in output.splitlines() if line.strip())
    return sorted(files)


def classify_dependency_config(files: list[str]) -> list[str]:
    patterns = (
        "build.gradle",
        "settings.gradle",
        "gradle/",
        "gradlew",
        "Dockerfile",
        "docker-compose",
        ".github/",
        "application",
        ".env.example",
    )
    return sorted(path for path in files if any(token in path for token in patterns))


def classify_migrations(files: list[str]) -> list[str]:
    return sorted(path for path in files if path.startswith("src/main/resources/db/migration/"))


def parse_test_results(root: pathlib.Path) -> list[TestResult]:
    result_dir = root / "build/test-results/test"
    if not result_dir.exists():
        return []
    results: list[TestResult] = []
    for xml_file in sorted(result_dir.glob("TEST-*.xml")):
        try:
            suite = ET.parse(xml_file).getroot()
        except ET.ParseError:
            continue
        results.append(
            TestResult(
                path=str(xml_file.relative_to(root)),
                tests=int(float(suite.attrib.get("tests", "0"))),
                failures=int(float(suite.attrib.get("failures", "0"))),
                errors=int(float(suite.attrib.get("errors", "0"))),
                skipped=int(float(suite.attrib.get("skipped", "0"))),
            )
        )
    return results


def build_artifacts(root: pathlib.Path) -> list[str]:
    libs = root / "build/libs"
    if not libs.exists():
        return []
    return sorted(str(path.relative_to(root)) for path in libs.glob("*.jar"))


def operations_signals(root: pathlib.Path) -> list[str]:
    signals: list[str] = []
    workflow_dir = root / ".github/workflows"
    if workflow_dir.exists():
        workflows = sorted(path.name for path in workflow_dir.glob("*.yml"))
        if workflows:
            signals.append(f"GitHub Actions workflows present: {', '.join(workflows)}")
    compose_file = root / "docker-compose.yml"
    if compose_file.exists():
        content = compose_file.read_text(encoding="utf-8")
        healthchecks = len(re.findall(r"^\s+healthcheck:", content, flags=re.MULTILINE))
        services = service_names_from_compose(content)
        if services:
            signals.append(f"Docker Compose services present: {', '.join(services)}")
        signals.append(f"Docker Compose healthcheck entries: {healthchecks}")
    if (root / "Dockerfile").exists():
        signals.append("Dockerfile present")
    return signals


def service_names_from_compose(content: str) -> list[str]:
    services: list[str] = []
    in_services = False
    for line in content.splitlines():
        if re.match(r"^services:\s*$", line):
            in_services = True
            continue
        if in_services and line and not line.startswith(" "):
            break
        match = re.match(r"^  ([A-Za-z0-9_.-]+):\s*$", line)
        if in_services and match:
            services.append(match.group(1))
    return services


def existing_docs(root: pathlib.Path, obsidian_root: pathlib.Path) -> list[str]:
    candidates = [
        root / "docs/resume-metrics.md",
        root / "docs/decision-log.md",
        obsidian_root / "resume-metrics.md",
        obsidian_root / "decision-log.md",
    ]
    summaries: list[str] = []
    for path in candidates:
        if not path.exists():
            continue
        content = path.read_text(encoding="utf-8")
        headings = len(re.findall(r"^#{1,6}\s+", content, flags=re.MULTILINE))
        summaries.append(f"`{path}` read ({len(content)} characters, {headings} headings)")
    return summaries


def collect_evidence(
    root: pathlib.Path,
    obsidian_root: pathlib.Path,
    target_date: dt.date,
    transcript_source: str | None,
) -> Evidence:
    prompt = read_required_prompt(root)
    commits = commits_for_date(root, target_date)
    files = changed_files_for_commits(root, commits)
    return Evidence(
        target_date=target_date,
        prompt_chars=len(prompt),
        agent_rule_files=read_agent_rule_files(root),
        git_status=git_status(root),
        commits=commits,
        changed_files=files,
        dependency_config_changes=classify_dependency_config(files),
        migration_changes=classify_migrations(files),
        test_results=parse_test_results(root),
        build_artifacts=build_artifacts(root),
        operations_signals=operations_signals(root),
        existing_docs=existing_docs(root, obsidian_root),
        transcript_source=transcript_source,
    )


def bullet_list(items: list[str], empty: str) -> str:
    if not items:
        return f"- {empty}\n"
    return "".join(f"- {item}\n" for item in items)


def commit_lines(commits: list[GitCommit]) -> list[str]:
    return [f"`{commit.sha}` ({commit.author_date}) {commit.subject}" for commit in commits]


def test_result_lines(results: list[TestResult]) -> list[str]:
    lines: list[str] = []
    for result in results:
        status = "verified pass" if result.failures == 0 and result.errors == 0 else "verified failure"
        lines.append(
            f"{result.path}: {status}; tests={result.tests}, passed={result.passed}, "
            f"failures={result.failures}, errors={result.errors}, skipped={result.skipped}"
        )
    return lines


def resume_outcomes(evidence: Evidence) -> list[str]:
    outcomes: list[str] = []
    if evidence.test_results:
        total = sum(result.tests for result in evidence.test_results)
        failures = sum(result.failures + result.errors for result in evidence.test_results)
        if total and failures == 0:
            outcomes.append(
                f"Maintained a verified local test baseline with {total} tests passing in Gradle XML results."
            )
    if evidence.dependency_config_changes:
        outcomes.append(
            "Documented dependency/configuration work from verified changed files; metric status is needs stronger evidence."
        )
    if evidence.migration_changes:
        outcomes.append(
            "Documented database migration changes from verified changed files; schema impact requires explicit approved rationale."
        )
    if evidence.commits:
        outcomes.append(
            "Captured previous-day commit evidence for interview and resume follow-up without inferring unstated rationale."
        )
    return outcomes


def metric_candidates(evidence: Evidence) -> list[str]:
    candidates = [
        "Health check success rate: measure against a user-approved local or deployed URL with repeated requests.",
        "API response time: measure with a user-approved endpoint and command so daily values are comparable.",
    ]
    if evidence.dependency_config_changes:
        candidates.append(
            "Build stability after config changes: run `./gradlew test` and `./gradlew build`, then record pass/fail and duration."
        )
    return candidates


def daily_note(evidence: Evidence) -> str:
    transcript_line = (
        f"- Transcript source explicitly provided: `{evidence.transcript_source}`\n"
        if evidence.transcript_source
        else "- Transcript source unknown; no Codex conversation transcript was inspected.\n"
    )
    return f"""---
project: FaithLog
type: daily-resume-monitor
date: {evidence.target_date.isoformat()}
generated_by: {GENERATED_BY}
---

# {evidence.target_date.isoformat()} Daily Resume Monitor

## Evidence Reviewed

- Prompt: `{PROMPT_PATH}` ({evidence.prompt_chars} characters read at runtime)
{bullet_list(evidence.agent_rule_files, 'No agent rule file evidence recorded.')}\
{bullet_list([f'Git status: {line}' for line in evidence.git_status], 'Git status unavailable.')}\
{bullet_list(commit_lines(evidence.commits), 'No commits found for the target date.')}\
{bullet_list([f'Changed file: `{path}`' for path in evidence.changed_files], 'No commit-level changed files found for the target date.')}\
{bullet_list(evidence.existing_docs, 'No existing project/Obsidian metric or decision logs found.')}\
{transcript_line}
## Completed Changes

{bullet_list([f'`{path}`' for path in evidence.changed_files], 'No completed changes were verified from commits for this date.')}
## Dependency, Config, Migration Evidence

{bullet_list([f'Dependency/config change: `{path}`' for path in evidence.dependency_config_changes], 'No dependency or configuration changes were found in the target-date commits.')}\
{bullet_list([f'DB migration change: `{path}`' for path in evidence.migration_changes], 'No DB migration changes were found in the target-date commits.')}
## Test, Build, Deployment Signals

{bullet_list(test_result_lines(evidence.test_results), 'No Gradle XML test results were available locally.')}\
{bullet_list([f'Build artifact present: `{path}`' for path in evidence.build_artifacts], 'No local build artifacts were available.')}\
{bullet_list(evidence.operations_signals, 'No deployment or operations signals were discovered locally.')}
## Meaningful Metrics

{meaningful_metrics_section(evidence)}
## Metrics Candidates

{bullet_list(metric_candidates(evidence), 'No metric candidates identified.')}
## Troubleshooting Items

- No troubleshooting root cause is recorded unless backed by local test/build output or documented evidence.

## Risks And Pending Decisions

{pending_decision_lines(evidence)}
## Resume Bullet Candidates

{bullet_list(resume_bullet_candidates(evidence), 'No resume bullet candidate was created because no meaningful verified work was found.')}
## Interview Explanation Candidates

- Explain what evidence was reviewed and which claims were deliberately left as pending instead of guessed.
- Explain how verified test/build/deployment signals become resume-grade metrics only after a stable measurement method exists.
"""


def meaningful_metrics_section(evidence: Evidence) -> str:
    lines: list[str] = []
    if evidence.test_results:
        total = sum(result.tests for result in evidence.test_results)
        failures = sum(result.failures + result.errors for result in evidence.test_results)
        confidence = "verified" if total else "candidate"
        lines.append(
            f"- Local test result: {total} tests, {failures} failures/errors. "
            f"Measurement method: Gradle XML under `build/test-results/test`. Confidence: {confidence}."
        )
    if evidence.build_artifacts:
        lines.append(
            "- Build artifacts present locally. Measurement method: `build/libs/*.jar`. Confidence: partially verified."
        )
    if not lines:
        lines.append("- No resume-grade measured metric was added for this date.")
    return "\n".join(lines) + "\n"


def pending_decision_lines(evidence: Evidence) -> str:
    lines = []
    if not evidence.transcript_source:
        lines.append(
            "- Pending decision: Where should the daily monitor read Codex conversation transcripts from, if transcript context should be included?"
        )
    lines.append(
        "- Pending decision: Which health/latency target should be used before recording comparable operational metrics?"
    )
    return "\n".join(lines) + "\n"


def resume_bullet_candidates(evidence: Evidence) -> list[str]:
    bullets: list[str] = []
    for outcome in resume_outcomes(evidence):
        bullets.append(f"[needs stronger evidence] {outcome} Evidence: daily note for {evidence.target_date}.")
    return bullets


def engineering_note(evidence: Evidence) -> str:
    return f"""---
project: FaithLog
type: engineering-wiki
date: {evidence.target_date.isoformat()}
generated_by: {GENERATED_BY}
---

# {evidence.target_date.isoformat()} Verified Engineering Knowledge

## Scope

This page records reusable engineering knowledge from verified previous-day evidence only.

## Changed Areas

{bullet_list([f'`{path}`' for path in evidence.changed_files], 'No commit-level changed files were verified.')}
## Evidence-Backed Notes

- Product direction, architecture rationale, schema/API/security/deployment meaning, and tradeoffs are not inferred unless documented in commits, code, docs, tests, or approved decisions.
- Commit subjects and changed file paths are evidence that work happened; they are not treated as complete rationale by themselves.

## Open Questions

{pending_decision_lines(evidence)}
## Evidence Links

{bullet_list(commit_lines(evidence.commits), 'No commits found for the target date.')}
"""


def troubleshooting_note(evidence: Evidence) -> str:
    section = f"""## {evidence.target_date.isoformat()} Automated Review

- Problem: No troubleshooting item was promoted from this run without verified symptoms and root cause.
- Symptoms: Not recorded.
- Root cause: Not recorded.
- Fix: Not recorded.
- Validation: {', '.join(test_result_lines(evidence.test_results)) if evidence.test_results else 'No local test result evidence available.'}
- Remaining risk: Transcript source and health/latency target remain pending decisions.
"""
    return section


def bullet_bank_section(evidence: Evidence) -> str:
    return f"""## {evidence.target_date.isoformat()}

{bullet_list(resume_bullet_candidates(evidence), 'No resume bullet candidate was created because no meaningful verified work was found.')}"""


def metrics_section(evidence: Evidence) -> str:
    return f"""### {evidence.target_date.isoformat()} Automated Resume Monitor

- Evidence source: `{PROMPT_PATH}` read at runtime.
- Commits reviewed: {len(evidence.commits)}
- Changed files reviewed: {len(evidence.changed_files)}
- Dependency/config changes reviewed: {len(evidence.dependency_config_changes)}
- DB migration changes reviewed: {len(evidence.migration_changes)}
{meaningful_metrics_section(evidence)}
Metric candidates:
{bullet_list(metric_candidates(evidence), 'No metric candidates identified.')}"""


def decision_section(evidence: Evidence) -> str:
    transcript_action = (
        "Transcript source was explicitly provided, but transcript content still must be verified against code, commits, tests, docs, or approved decisions."
        if evidence.transcript_source
        else "No transcript source was provided, so conversation transcripts were not inspected."
    )
    return f"""### {evidence.target_date.isoformat()} - {PENDING_TRANSCRIPT_TITLE}

- Context: The daily resume monitor can only use Codex or assistant transcripts when their source/location is explicitly available and verifiable.
- Pending question: Where should the daily monitor read Codex conversation transcripts from, if transcript context should be included?
- Recommendation: Provide one stable local transcript source path or leave transcript analysis disabled.
- Current action: {transcript_action}
"""


def generated_block(name: str, date: dt.date, content: str) -> str:
    key = f"{name}:{date.isoformat()}"
    return (
        f"<!-- {GENERATED_BY}:start:{key} -->\n"
        f"{content.rstrip()}\n"
        f"<!-- {GENERATED_BY}:end:{key} -->\n"
    )


def upsert_generated_block(path: pathlib.Path, name: str, date: dt.date, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    block = generated_block(name, date, content)
    if not path.exists():
        path.write_text(block, encoding="utf-8")
        return
    text = path.read_text(encoding="utf-8")
    key = f"{name}:{date.isoformat()}"
    pattern = re.compile(
        rf"<!-- {GENERATED_BY}:start:{re.escape(key)} -->.*?<!-- {GENERATED_BY}:end:{re.escape(key)} -->\n?",
        flags=re.DOTALL,
    )
    if pattern.search(text):
        updated = pattern.sub(block, text)
    else:
        updated = text.rstrip() + "\n\n" + block
    path.write_text(updated, encoding="utf-8")


def write_outputs(root: pathlib.Path, obsidian_root: pathlib.Path, evidence: Evidence) -> list[pathlib.Path]:
    outputs: list[pathlib.Path] = []

    daily = daily_note(evidence)
    engineering = engineering_note(evidence)
    for base in [root / "docs/wiki", obsidian_root / "wiki"]:
        daily_path = base / "daily" / f"{evidence.target_date.isoformat()}.md"
        engineering_path = base / "engineering" / f"{evidence.target_date.isoformat()}-verified-work.md"
        daily_path.parent.mkdir(parents=True, exist_ok=True)
        engineering_path.parent.mkdir(parents=True, exist_ok=True)
        daily_path.write_text(daily, encoding="utf-8")
        engineering_path.write_text(engineering, encoding="utf-8")
        upsert_generated_block(
            base / "troubleshooting.md",
            "troubleshooting",
            evidence.target_date,
            troubleshooting_note(evidence),
        )
        upsert_generated_block(
            base / "resume-bullet-bank.md",
            "resume-bullet-bank",
            evidence.target_date,
            bullet_bank_section(evidence),
        )
        outputs.extend(
            [
                daily_path,
                engineering_path,
                base / "troubleshooting.md",
                base / "resume-bullet-bank.md",
            ]
        )

    for base in [root / "docs", obsidian_root]:
        upsert_generated_block(
            base / "resume-metrics.md",
            "resume-metrics",
            evidence.target_date,
            metrics_section(evidence),
        )
        upsert_generated_block(
            base / "decision-log.md",
            "decision-log",
            evidence.target_date,
            decision_section(evidence),
        )
        outputs.extend([base / "resume-metrics.md", base / "decision-log.md"])

    return sorted(set(outputs))


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=pathlib.Path, default=pathlib.Path.cwd())
    parser.add_argument("--obsidian-root", type=pathlib.Path, default=DEFAULT_OBSIDIAN_ROOT)
    parser.add_argument("--date", type=lambda value: dt.date.fromisoformat(value), default=None)
    parser.add_argument("--today", type=lambda value: dt.date.fromisoformat(value), default=None)
    parser.add_argument("--transcript-source", default=None)
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    root = args.root.resolve()
    obsidian_root = args.obsidian_root.expanduser().resolve()
    target_date = args.date or previous_day(args.today)
    evidence = collect_evidence(root, obsidian_root, target_date, args.transcript_source)
    outputs = write_outputs(root, obsidian_root, evidence)
    print(f"Daily resume monitor completed for {target_date.isoformat()}.")
    print("Updated files:")
    for path in outputs:
        print(f"- {path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv[1:]))
    except FileNotFoundError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
