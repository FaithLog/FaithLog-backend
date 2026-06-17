# Daily Resume Monitor Prompt

Version: 2026-06-17

Additionally, convert the previous day's work into a resume-oriented engineering knowledge base.

Before interpreting any prior work, Codex conversation, commit, metric, or implementation detail, read and follow AGENTS.md and AGENT.md. The user remains the sole decision maker. Do not infer product direction, architecture intent, algorithm rationale, schema/API/security/deployment behavior, performance meaning, or implementation tradeoffs unless they are explicitly evidenced by commits, code, docs, tests, benchmark output, issue notes, or user-approved decisions. If rationale or direction is unclear, write the exact question that should be asked and record it as a pending decision.

Review available evidence from the previous day, including:
- Git commits, diffs, and changed files.
- Local docs and decision logs.
- Test/build/benchmark output available locally.
- Database migrations and schema changes.
- Configuration, dependency, CI/CD, deployment, or operations changes.
- Codex or assistant conversation transcripts only if their source/location is explicitly available.
- Any existing Obsidian/project notes related to FaithLog.

Do not treat Codex conversation text as final truth by itself. Use it as context only. Confirm important claims against code, commits, tests, docs, or user-approved decisions. If a claim cannot be verified, label it as "unverified context" or convert it into a pending question.

Create or update Obsidian/project wiki notes that preserve reusable engineering knowledge for future resume writing and technical interviews. Focus on:
- What was changed.
- Why the change was made, only when evidence exists.
- What alternatives or tradeoffs were considered, only when evidence exists.
- Why a specific algorithm, architecture, schema, API, or implementation approach was used, only when explicitly documented or inferable from approved evidence.
- What measurable outcome was produced.
- What risk was reduced.
- What troubleshooting was performed.
- What should be asked or decided next.

Do not manufacture metrics. Record only meaningful resume-grade metrics that are supported by evidence. A metric is resume-grade only if it helps demonstrate one of the following:
- Reliability improvement.
- Performance improvement.
- Test coverage or verification strength.
- Build/deployment stability.
- Data/model/schema correctness.
- User-facing latency, throughput, availability, or error-rate improvement.
- Operational visibility or troubleshooting speed.
- Reduction in manual work, duplicated code, defects, risk, or complexity.
- Increased maintainability through measurable module, dependency, migration, or test changes.

Avoid weak or vanity metrics such as raw lines of code changed, number of files touched, or arbitrary percentage improvements unless they directly support an engineering outcome. If a metric is potentially useful but not yet measured, record it as a "metric candidate," including:
- What should be measured.
- Why it matters for resume/storytelling.
- How to measure it.
- What command, test, benchmark, endpoint, or query should produce it.

Maintain these note types when practical:

1. Daily Worklog
   Purpose: summarize the previous day's concrete work.
   Include:
   - Date.
   - Evidence reviewed.
   - Completed changes.
   - Verified test/build/deployment signals.
   - Meaningful metrics.
   - Troubleshooting items.
   - Risks and pending decisions.
   - Resume-ready bullet candidates.
   - Interview explanation candidates.

2. Engineering Wiki
   Purpose: preserve reusable explanations.
   Include:
   - Feature or module name.
   - Problem being solved.
   - Implemented approach.
   - Evidence-backed rationale.
   - Algorithm or architecture notes.
   - Tradeoffs and constraints.
   - Performance/security/data implications, only if evidenced.
   - Links to commits, files, migrations, tests, or decision-log entries.
   - Open questions.

3. Decision and Pending Question Index
   Purpose: separate approved decisions from unresolved assumptions.
   Update docs/decision-log.md and /Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/decision-log.md with:
   - User-approved decisions.
   - Date.
   - Evidence.
   - Impact.
   - Pending decisions/questions.
   - Owner: user, unless explicitly assigned otherwise.

4. Resume Metrics Log
   Purpose: maintain durable evidence for future resume bullets.
   Update /Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/resume-metrics.md and keep docs/resume-metrics.md aligned when practical.
   Include only supported, meaningful metrics.
   For each metric, record:
   - Metric name.
   - Before value, if known.
   - After value, if known.
   - Measurement method.
   - Command/source.
   - Date measured.
   - Related files/commits/tests.
   - Resume relevance.
   - Confidence level: verified, partially verified, or candidate.

5. Troubleshooting Log
   Purpose: preserve debugging stories for interviews.
   For each troubleshooting item, record:
   - Problem.
   - Symptoms.
   - Root cause, only if verified.
   - Fix.
   - Before/after metric or observable behavior.
   - Prevention added or recommended.
   - Tests or checks that validate the fix.
   - Remaining risk.

6. Resume Bullet Bank
   Purpose: collect future resume bullets without exaggeration.
   Add bullet candidates only when meaningful work happened.
   Each bullet should include:
   - Action verb.
   - Technical scope.
   - Measurable result, if verified.
   - Business/user/engineering impact.
   - Evidence link.
   Mark bullets as:
   - ready
   - needs metric
   - needs user approval
   - needs stronger evidence

When creating wiki content, keep Markdown suitable for Obsidian. Use clear headings, backlinks when practical, and concise bullet points. Prefer durable project knowledge over verbose daily narration.

Suggested output locations, only if approved or already present:
- docs/resume-metrics.md
- docs/decision-log.md
- docs/wiki/daily/YYYY-MM-DD.md
- docs/wiki/engineering/
- docs/wiki/troubleshooting.md
- docs/wiki/resume-bullet-bank.md
- /Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/resume-metrics.md
- /Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/decision-log.md
- /Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/wiki/

If a wiki path or transcript source is not explicitly approved or does not exist, do not create arbitrary structure silently. Record a pending decision asking where the user wants the wiki and Codex transcript summaries stored.

At the end of the report, include:
- Resume-ready outcomes.
- Metrics added today.
- Metrics candidates.
- Wiki pages created or updated.
- Pending decisions for the user.
- Testing/build/deployment checks run and their results.
- Testing/build/deployment checks not run, why they matter, and what metric they would produce.
