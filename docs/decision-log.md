# FaithLog Decision Log

This file records user-approved project decisions so Codex does not rely on guesses later.

## Rules

- Every product, architecture, data, deployment, test-strategy, or resume-metric decision must come from the user.
- If Codex is unsure, Codex must ask before implementation.
- Record the decision date, context, options if relevant, the user's decision, and the implementation impact.

## Decisions

### 2026-06-16 - User Owns All Project Decisions

- Context: The user stated that Codex must not develop or implement based on guesses.
- Decision: All ambiguous or unusual decisions must be asked of the user before implementation.
- Impact: Codex must stop and ask before choosing product behavior, architecture, schema, deployment, test strategy, monitoring scope, resume metrics, or implementation tradeoffs.

### 2026-06-16 - Track Resume Metrics During Development

- Context: The project will be deployed and operated, and the user wants resume-ready quantitative evidence.
- Decision: Codex should record measurable project progress, tests, troubleshooting, and improvements in Markdown and Obsidian.
- Impact: Metrics are tracked in `docs/resume-metrics.md` and mirrored to the Obsidian FaithLog note.

### 2026-06-16 - Backend API And Issue Policy Alignment

- Context: The user provided final backend policy decisions for auth, FCM, poll comments, devotion APIs, coffee charge automation, issue status management, and MVP exclusions.
- Decision: GitHub Issues must follow the final policies recorded in `docs/backend-implementation-policy.md`.
- Impact: Issues #21, #27, #28, #31, #38, #39, and #40 were updated with final policy details. Manual `칸반 상태:` lines were removed from #17~#41 where present so GitHub Project Board Status remains the source of truth.

### 2026-06-16 - Codex Hook Development Rules

- Context: The user requested a Codex Hook document that consolidates TDD, final FaithLog design rules, architecture rules, security rules, forbidden-term checks, test rules, Obsidian documentation, and GitHub Issue/Project workflow.
- Decision: Use `AGENTS.md` as the single Agent rule file, and put detailed development hook rules in `docs/codex/FAITHLOG_CODEX_HOOK.md`.
- Impact: Issue #43 was created and connected to the FaithLog Backend Kanban board. The board card was moved to `In Progress`. `PollComment` was not treated as a forbidden term because poll comments are MVP scope in #38.

### 2026-06-16 - Agent Rule File Consolidation

- Context: The user requested that Agent rules be merged into one file.
- Decision: `AGENTS.md` is the single source of Agent instructions for Codex in this repository.
- Impact: The former `AGENT.md` content was merged into `AGENTS.md`, and repository documentation now points to `AGENTS.md`.

### 2026-06-16 - Poll Template Default Policy

- Context: The user clarified which poll templates should exist by default.
- Decision: Only the coffee poll template should be provided as a default template. Wednesday worship, Saturday shepherd meeting, and custom poll templates should be created by an admin.
- Impact: Issue #37 and Codex Hook rules must treat coffee as the only default poll template. Other poll templates are admin-created and must not be silently seeded unless the user later approves a new decision.

### 2026-06-16 - Poll Template Weekly Auto Generation Setting

- Context: The user asked whether polls can be generated automatically every week, then decided that admins should be able to set this when creating custom/admin-created templates.
- Decision: Poll templates can include a weekly auto-generation setting chosen by the admin at template creation time. Templates without auto-generation enabled are used only for manual poll creation.
- Impact: Issue #37 must capture the template setting/API scope, and Issue #24 must execute enabled template schedules with duplicate prevention.

### 2026-06-16 - Coffee Duty Poll Time Settings

- Context: The user clarified that Notion ERD includes coffee poll timing design.
- Decision: The coffee duty assignee can set the weekly coffee poll auto-generation time and the coffee poll close time.
- Impact: Issue #37 must store these timing settings on the coffee poll template according to the Notion ERD column names. Issue #24 must use those settings when generating and closing weekly coffee polls.

## Pending Decisions

### 2026-06-16 - Poll Comment Issue Split

- Context: Poll comments are now MVP scope. Issue #38 contains the PollComment implementation scope.
- Pending question: Should PollComment stay inside #38, or should a separate `[Feat] 투표 댓글 구현` issue be created?
- Current action: No new issue was created because the user said to create it only if needed, and #38 now contains the required implementation scope.

### 2026-06-16 - Project Board Domain Fields For Mixed Domains

- Context: Project Board items #23 and #24 have mixed domain text in their issue bodies, but the board Domain field is single-select.
- Pending question: Should #23 use `global` or another convention for `global/domain-admin`, and should #24 use `global` or `notification` for `global/notification`?
- Current action: Domain was left blank for #23 and #24 to avoid guessing.

### 2026-06-16 - Obsidian FaithLog Folder Path

- Context: The current Hook uses `/Users/josephuk77/obsidian/obsidian-writing-vault/Projects/FaithLog/`, while one attached requirement may imply `04_Projects/FaithLog`.
- Pending question: Should FaithLog Obsidian notes live under `Projects/FaithLog` or `04_Projects/FaithLog`?
- Current action: The path was not changed to avoid guessing the user's vault structure.
