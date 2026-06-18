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

### 2026-06-17 - Devotion Penalty Table

- Context: The user provided the current devotion check notice used by the ministry team.
- Decision: Devotion penalty rules use a 5-day weekly standard. Quiet time missing count is charged at 500 KRW per day, prayer missing count is charged at 500 KRW per day, Bible reading missing count is charged at 300 KRW per day, and Saturday lateness is charged as 1,000 KRW base plus 100 KRW per late minute. For the referenced week, lateness minutes are all 0.
- Impact: Issue #32 must seed or configure `penalty_rules` to support `QUIET_TIME`, `PRAYER`, `BIBLE_READING`, and `SATURDAY_LATE` with these amounts. Issue #33 must calculate one combined weekly `PENALTY` charge per `weekly_devotion_records.id`.

### 2026-06-17 - Final Implementation Overrides For Conflicting Old Specs

- Context: The user approved the implementation direction previously identified by Codex for conflicts between older Notion/API drafts and the latest local decisions.
- Decision: Refresh Token storage follows Redis allowlist and not the old `refresh_tokens` table design. Poll responses must use request field `optionIds` and `poll_response_options`, not request field `optionId` or `poll_responses.option_id`. Devotion implementation uses the weekly `PUT /api/v1/campuses/{campusId}/devotions/me/weeks/{weekStartDate}` flow to create or update 7 daily rows; the old single-day devotion API is not the MVP implementation path.
- Impact: Auth, poll, and devotion issues must treat these decisions as higher priority than older Notion API text. Any implementation or API documentation that still exposes the old paths/fields must be corrected before development is considered complete.

### 2026-06-17 - Pagination Sorting Redis TTL And Notification Failure Policies

- Context: The user approved the recommended implementation policies for list APIs, Redis token TTLs, and notification failure handling.
- Decision: List APIs use common pagination query parameters `page`, `size`, and `sort`; default page is 0, default size is 20, maximum size is 100, and default sorting is latest-first unless a domain has an explicit order such as poll option `sortOrder,asc`. Access token blacklist TTL uses the access token remaining lifetime plus 60 seconds. Refresh token allowlist TTL uses the refresh token expiration. Refresh token reuse-detection keys, when used, live until the refresh token expiration. Notification sends do not retry automatically in MVP. Success, failure, and skip results are all saved to `notification_logs`; invalid or unregistered FCM token errors deactivate the affected token.
- Impact: Repository query APIs, Redis auth infrastructure, and notification services must implement these defaults and cover them with tests where applicable.

### 2026-06-17 - Lateness Penalty Calculation

- Context: The user confirmed the intended interpretation of the Saturday lateness penalty rule.
- Decision: If `saturdayLateMinutes = 0`, the lateness penalty is 0 KRW. If `saturdayLateMinutes > 0`, the lateness penalty is `1,000 + saturdayLateMinutes * 100` KRW.
- Impact: Issue #32 must implement this conditional formula in the devotion penalty calculator.

### 2026-06-17 - Campus Creation Includes Penalty Account And Penalty Rules

- Context: The user identified that creating the campus penalty account and penalty rules at campus creation time reduces later runtime exceptions in devotion submission.
- Decision: Campus creation must also create the campus penalty account and default penalty rules. The campus creation request/flow must collect or receive enough penalty account information to create the active `PENALTY` payment account, and must initialize the default devotion penalty rules from the approved penalty table.
- Impact: Issue #29 and Issue #34 must be aligned so campus onboarding creates the required billing prerequisites. Issue #33 can assume a properly onboarded campus has an active `PENALTY` account, while still returning a clear error if the account is missing due to legacy or corrupted data.

### 2026-06-17 - Coffee Poll Requires Coffee Duty Assignment

- Context: The user decided that coffee poll behavior should fail clearly when no coffee duty assignee exists.
- Decision: If a coffee poll flow requires a coffee duty assignee and no active `CampusDutyAssignment` with `DutyType.COFFEE` exists for the campus, the API must fail with a clear user-facing message: `관리자에게 문의하세요`.
- Impact: Issue #30 must provide active coffee duty assignment management, and Issue #37/#39 must validate the assignment before coffee poll setup or coffee charge flow where required.

### 2026-06-17 - Prayer Requests Group Board

- Context: The user described a weekly Saturday prayer request workflow where each sharing group collects member prayer requests and currently posts them as one KakaoTalk message. The user decided the app should replace the message view instead of generating KakaoTalk share text.
- Decision: Add a prayer request feature where all campus members can view the weekly prayer requests for all groups on one page. Prayer groups are managed inside an active prayer season that can start without a fixed end date and be manually closed when groups change. Prayer request input should also work on one page so a user with permission can enter multiple group members' weekly prayer requests together. KakaoTalk sharing is not MVP scope for this feature.
- Impact: A new prayer domain should be planned with prayer seasons, prayer groups, group members, weekly prayer boards, and member submissions. The read API must support one-page grouped output for the whole campus.

### 2026-06-17 - Prayer Request Editing And Conflict Policy

- Context: The user was concerned about simultaneous edits when multiple people can edit prayer requests from one page.
- Decision: Store prayer requests per member submission rather than as one large page blob. A weekly prayer submission can have nullable content for cases where there is no meeting or no request to write. Each member submission must use version-based optimistic locking. The client sends the current version when saving; the server saves only if the version still matches and otherwise returns a conflict instead of silently overwriting another edit.
- Impact: Prayer request write APIs must support partial person-level saves and `409 Conflict` behavior. The UI can show all members on one page, but persistence and conflict detection are scoped to each member's submission row.

### 2026-06-17 - Prayer Request No-Meeting And Writing Status Policy

- Context: The user clarified that prayer requests are still written even when there is no meeting, and that prayer requests do not need a separate submission deadline.
- Decision: `NO_MEETING`, if used, is not a blocking prayer request submission state. It only describes meeting schedule/context. Prayer request writing remains available even if there is no meeting. Because there is no separate deadline, do not split "not written yet" and "missing submission" into separate states; model/display prayer request writing as not written vs written.
- Impact: Issue #45 must not treat `NO_MEETING` as a reason to disable prayer request entry or as a missing-submission state. Schema/API/UI planning should keep meeting status separate from person-level prayer request content/submission status. The exact storage scope for meeting status, such as whole prayer week vs group-week, still needs user confirmation before schema implementation.

### 2026-06-17 - Daily Resume Monitor Manual Automation

- Context: The user requested a daily resume monitoring automation that reviews previous-day FaithLog work and writes resume-oriented Markdown notes in both project docs and the Obsidian vault.
- Decision: Implement the monitor as a manual script first, store its versioned prompt at `docs/prompts/daily-resume-monitor.md`, read the prompt and Agent rules each run, write only evidence-backed notes/metrics, record unverified items as pending decisions, and do not schedule the automation until a scheduling policy is explicitly approved.
- Impact: `python3 scripts/daily_resume_monitor.py` is the approved manual entry point. The monitor may update `docs/resume-metrics.md`, `docs/decision-log.md`, `docs/wiki/`, and the approved Obsidian FaithLog path, but it must not invent metrics or infer product/architecture/schema/API/security/deployment/testing direction.

### 2026-06-17 - Defer Flyway Until Feature Development Is Complete

- Context: The user decided that Flyway should be introduced after the main feature development work is complete, rather than used as the active schema mechanism during early feature implementation.
- Decision: During feature development, do not treat Flyway migrations as the primary implementation deliverable. Final Flyway migration scripts should be organized near the end of development after the approved domain model has stabilized.
- Impact: GitHub Projects should include a later infra/build task for final Flyway migration consolidation. Feature issues may define schema requirements and tests, but should not be blocked on final Flyway script authoring unless the user later changes this policy.

### 2026-06-17 - Remove Flyway From Active Runtime

- Context: The user explicitly requested removing Flyway now before raising the PR again.
- Decision: Remove Flyway Gradle dependencies, remove `spring.flyway` runtime configuration, and remove the placeholder Flyway migration file from the active codebase. Flyway remains deferred until the main feature development work is complete.
- Impact: Early feature development will rely on approved schema requirements and persistence tests instead of active Flyway migration scripts. A later infra/build task should reintroduce consolidated migrations when the domain model stabilizes.

### 2026-06-17 - Local Docker Uses JPA DDL Auto Update During Development

- Context: #27 Docker verification reached the application boot step after the local Postgres credential mismatch was resolved, but the app could not start against an empty local Docker database while Flyway remains deferred and `ddl-auto=validate` was active.
- Decision: For local Docker development verification only, default `SPRING_JPA_HIBERNATE_DDL_AUTO` to `update` so Hibernate can create or update the local development schema. Keep the value environment-overridable.
- Impact: Local Docker can boot and serve `GET /api/v1/health` during early feature development before final Flyway migration consolidation. This does not change the deferred Flyway policy or define a production migration strategy.

### 2026-06-17 - API Documentation Uses Spring REST Docs For Detailed Contracts

- Context: The user clarified that Swagger/springdoc should remain available for simple API exploration, but the codebase should not be cluttered with Swagger documentation annotations on Controllers, DTOs, or Entities.
- Decision: Swagger/springdoc is kept. Swagger annotation-centered documentation is not used as the main documentation approach. Detailed request/response API contracts are verified and documented through Spring REST Docs tests and generated snippets/asciidoc.
- Impact: New APIs or changed APIs should add MockMvc/WebMvc/Spring REST Docs tests where practical. Controllers, DTOs, and Entities must not be polluted with documentation-only Swagger annotations such as `@Operation`, `@Schema`, or `@ApiResponse`.

### 2026-06-17 - FCM Token Lifecycle Policy

- Context: The user clarified that FCM tokens are issued by the frontend Firebase SDK, not by the backend, and asked whether token expiration/staleness handling is included in the plan.
- Decision: The frontend must fetch the current FCM token on app entry/login and send it to the backend. The backend stores FCM tokens in `user_fcm_tokens` as the source of truth and handles the registration API as an idempotent upsert. Redis is not the source of truth for FCM tokens. The backend stores a frontend-generated `clientInstanceId` to identify the app installation, updates `lastSeenAt` and `lastRefreshedAt`, deactivates previous tokens for the same user/client instance when a token changes, deactivates token ownership for another user when the same token is re-registered, deactivates the current device token on logout, and excludes or deactivates tokens stale for 90 days. `UNREGISTERED`/token-not-registered failures deactivate the token immediately, while `INVALID_ARGUMENT` deactivates the token only when the payload is known to be valid.
- Impact: Issue #40, Notion planning, ERD, and API design must include FCM token lifecycle fields and upsert/stale-token behavior. Issue #24 or a later scheduler task may clean up stale tokens in batch, but send-time filtering must not target inactive or stale tokens.

### 2026-06-17 - Auth Refresh Logout Contract For Issue 28

- Context: Issue #28 needed clarification before development so Codex would not guess the refresh/logout request contracts, session rotation behavior, FCM dependency boundary, or REST Docs expectations.
- Decision: `POST /api/v1/auth/refresh` receives `refreshToken` in the JSON request body and returns the same token response shape as login. Refresh rotation keeps the same `sessionId` and replaces the refresh token identifier. `POST /api/v1/auth/logout` requires `Authorization: Bearer {accessToken}` and accepts optional body fields `refreshToken`, `clientInstanceId`, and `fcmToken`; logout must still succeed without FCM fields. Issue #28 should not implement Notification entities directly. It may define and call an application port for current-device FCM deactivation, while #40 owns the actual `user_fcm_tokens` persistence implementation. New/changed auth APIs should add Spring REST Docs tests.
- Impact: Issue #28, Notion auth API pages, backend policy, and the Codex hook must align on this contract before the development session starts. Tests must cover refresh rotation, old refresh token reuse, logout blacklist/allowlist deletion, optional FCM fields, no raw token storage, Redis TTLs, and REST Docs snippets.

## Pending Decisions

### 2026-06-17 - Prayer Request Meeting Status Storage Scope

- Context: Prayer request writing remains available even when there is no meeting, so meeting status must be separated from whether a prayer request can be written. The remaining unresolved decision is only where, if anywhere, meeting status should be stored.
- Pending question: Should `NO_MEETING` or an equivalent meeting status be stored at the whole prayer week level, at the group-week level, both, or omitted from MVP?
- Recommendation: If FaithLog only needs to display campus-wide off-weeks, keep `NO_MEETING` on `prayer_weeks`. If individual prayer groups can skip independently and the app must display that, add a group-week status model. If the app does not need to display meeting status, omit `NO_MEETING` from MVP and rely on nullable content plus written/not-written status.
- Current action: Prayer request writing availability and no-deadline status policy are approved; exact meeting status storage scope must be confirmed before schema implementation.

### 2026-06-17 - Daily Health And Response-Time Measurement Scope

- Context: The codebase exposes `GET /api/v1/health`, and the daily monitor can record health/latency metrics only if the measurement target and runtime are user-approved.
- Pending question: For daily monitoring, should Codex measure health/response time against a local booted app, a deployed environment URL, or leave health/latency as manual-only until the user defines the target?
- Recommendation: Approve one source of truth for daily health metrics first so the monitor can report comparable numbers instead of mixed local/deploy timings.
- Current action: Today's report records that the endpoint exists but does not publish latency or availability percentages.

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

<!-- daily-resume-monitor:start:decision-log:2026-06-16 -->
### 2026-06-16 - Daily Resume Monitor Transcript Source

- Context: The daily resume monitor can only use Codex or assistant transcripts when their source/location is explicitly available and verifiable.
- Pending question: Where should the daily monitor read Codex conversation transcripts from, if transcript context should be included?
- Recommendation: Provide one stable local transcript source path or leave transcript analysis disabled.
- Current action: No transcript source was provided, so conversation transcripts were not inspected.
<!-- daily-resume-monitor:end:decision-log:2026-06-16 -->
