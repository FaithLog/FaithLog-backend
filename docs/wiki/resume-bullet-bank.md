<!-- issue-work:start:resume-bullet-bank:2026-07-13-188 -->
## 2026-07-13 Issue #188

- [ready] 관리자 주차별 경건·실제 벌금 조회와 2-sheet XLSX export를 동일 bulk query model로 구현해 campus 격리와 N+1 회귀를 고정하고, 82개 focused·420개 전체 테스트와 REST Docs 126개 스니펫 기준으로 권한·집계·파일 계약을 검증했다. Evidence: `docs/resume-metrics.md` 2026-07-13 #188.
<!-- issue-work:end:resume-bullet-bank:2026-07-13-188 -->

<!-- daily-resume-monitor:start:resume-bullet-bank:2026-06-16 -->
## 2026-06-16

- [needs stronger evidence] Maintained a verified local test baseline with 1 tests passing in Gradle XML results. Evidence: daily note for 2026-06-16.
- [needs stronger evidence] Captured previous-day commit evidence for interview and resume follow-up without inferring unstated rationale. Evidence: daily note for 2026-06-16.
<!-- daily-resume-monitor:end:resume-bullet-bank:2026-06-16 -->

<!-- daily-resume-monitor:start:resume-bullet-bank:2026-06-17 -->
## 2026-06-17

- [ready] Implemented Redis-backed JWT refresh/logout flows in Spring Boot and verified the branch with 21 passing local tests, successful build, and 10 REST Docs snippet groups. Evidence: `2026-06-17 Daily Resume Monitor`.
- [ready] Added refresh rotation, reused-token rejection, logout invalidation, and optional current-device FCM deactivation coverage while keeping notification persistence behind an application port. Evidence: `2026-06-17 Auth Refresh Logout Redis`.
- [needs metric] API contract documentation generation is stable through `./gradlew asciidoctor`; add one approved runtime health metric to strengthen the resume story. Evidence: `2026-06-17 Daily Resume Monitor`.
<!-- daily-resume-monitor:end:resume-bullet-bank:2026-06-17 -->

<!-- issue-work:start:resume-bullet-bank:2026-07-11-155 -->
## 2026-07-11 Issue #155

- [ready] Batch/Scheduler의 Poll·자동 알림·FCM cleanup 책임을 6개 전용 use case로 분리하고 121줄/296줄 통합 Service를 29줄/34줄 호환 facade로 76.0%/88.5% 축소했으며, 5개 구조 게이트와 368개 전체 테스트로 스케줄·정산·Redis fail-closed·retention 정책 무변경을 검증했다. Evidence: `docs/resume-metrics.md` #155, `docs/wiki/engineering/2026-07-11-batch-scheduler-usecase-separation.md`.
<!-- issue-work:end:resume-bullet-bank:2026-07-11-155 -->

<!-- issue-work:start:resume-bullet-bank:2026-07-12-157 -->
## 2026-07-12 Issue #157

- [ready] Spring Boot API 80개와 service authorization guard를 전수 대조하고, 7개 신뢰 경계·11개 보호 자산·18개 객체 식별자 공격 표면의 위협 모델을 구축해 마지막 active ADMIN 탈퇴 우회 1건(Medium, confidence 10/10)을 코드 수정 없이 확정했다. Evidence: `docs/security/157-threat-model.md`, `docs/security/157-api-authorization-matrix.md`, `docs/security/157-audit-findings.md`.
<!-- issue-work:end:resume-bullet-bank:2026-07-12-157 -->
