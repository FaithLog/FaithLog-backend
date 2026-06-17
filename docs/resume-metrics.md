# FaithLog Resume Metrics Log

FaithLog를 운영 가능한 프로젝트로 만들면서 이력서에 사용할 수 있는 정량 지표, 테스트 결과, 트러블슈팅 내역을 누적 기록한다.

## 기록 원칙

- 가능한 모든 개선은 수치로 남긴다.
- 테스트가 필요하다고 판단되면 테스트 항목, 이유, 기대 지표를 먼저 적는다.
- 장애, 버그, 성능 저하, 설정 문제는 원인, 해결, 재발 방지, 전후 수치를 함께 기록한다.
- 이력서에 쓸 수 있는 문장 후보는 별도로 남긴다.

## 핵심 지표 후보

| 영역 | 지표 | 측정 방법 | 최신값 | 목표 |
| --- | --- | --- | --- | --- |
| 품질 | 테스트 통과율 | `./gradlew test` | 100% (2026-06-17) | 100% |
| 품질 | 테스트 코드 파일 수 | `rg --files src/test` | 1 test source, 1 test resource | 증가 추적 |
| 안정성 | 빌드 성공 여부 | `./gradlew build` | 성공 (2026-06-17) | 성공 |
| API | 응답 시간 | 로컬/운영 부하 테스트 | 측정 보류 (2026-06-17) | TBD |
| 운영 | 헬스체크 성공률 | `/health` 또는 배포 플랫폼 상태 | 측정 보류 (2026-06-17) | 99%+ |
| 유지보수 | 주요 모듈 수 | 패키지/도메인 기준 | 7 top-level modules (2026-06-17) | 추적 |
| 데이터 | DB 마이그레이션 수 | `src/main/resources/db/migration` | 0 (Flyway deferred, 2026-06-17) | 추적 |

## Daily Monitoring Notes

### 2026-06-17

- 브랜치/작업트리:
  - 현재 브랜치: `chore/codex-hook-dev-rules`
  - `git status --short --branch`: 워크트리 변경 0건
  - `develop` 대비 최근 커밋: 4개 (`9e0a6b0`, `65c6ba0`, `6845738`, `a5289e4`)
- 변경 범위 수치:
  - `develop..HEAD` diff: 파일 6개, 추가 1,179라인, 삭제 0라인
  - 앱 코드 변경 파일: 0개
  - 문서/운영 규칙 변경 파일: 6개 (`AGENTS.md`, `README.md`, `docs/*`)
- 코드베이스 구조 수치:
  - `src/main/java/com/faithlog` top-level 모듈: 7개 (`billing`, `campus`, `devotion`, `global`, `notification`, `poll`, `user`)
  - Java 소스 파일: 36개
  - 실구현 Java 파일(`package-info.java` 제외): 9개
  - `package-info.java`: 27개
  - 테스트 파일: 1개
  - 테스트 리소스 파일: 1개
  - DB 마이그레이션 파일: 0개 (Flyway deferred)
- 의존성/설정 관찰:
  - `build.gradle.kts`에서 Flyway 런타임 의존성 제거
  - 핵심 런타임 의존성 유지: Spring Boot 3.5.0, Java 21, JPA, Redis, Security, PostgreSQL, Firebase Admin, JWT
  - Netty override 유지: `4.1.135.Final`
- 운영 신호:
  - 코드상 헬스 엔드포인트 존재: `GET /api/v1/health`
  - GitHub Actions workflow 파일: 2개 (`ci.yml`, `project-docs-check.yml`)
  - `ci.yml` 로컬 기준 품질 게이트 job: 2개 (`spring-boot`, `docker`)
  - Docker Compose 로컬 서비스: 5개 (`postgres`, `redis`, `pgadmin`, `redis-commander`, `app`)
  - Docker Compose 명시 healthcheck: 2개 (`postgres`, `redis`)
  - 응답 시간/헬스 성공률은 측정 대상 환경이 승인되지 않아 오늘 수치 미기록
- 오늘 테스트 후보:
  - `docker compose up -d postgres redis` 후 앱 기동 + `curl /api/v1/health` 측정
  - 이유: 현재 엔드포인트는 존재하지만 승인된 런타임 기준의 일별 헬스/지연시간 기준선이 없다
  - 기대 지표: 앱 기동 성공 여부, HTTP 200 여부, 응답 시간(ms), 연속 성공률(%)

### 2026-06-16

- 자동화 목표: 매일 오전 6시에 프로젝트 상태를 확인하고, 수치화 가능한 변경과 개선 포인트를 보고한다.
- 기록 위치: 이 파일. Obsidian Vault 경로를 받으면 Vault 내부 문서로 옮기거나 동기화한다.
- 다음 테스트 후보:
  - `./gradlew test`로 기본 테스트 통과율 확보
  - `./gradlew build`로 배포 전 빌드 안정성 확보
  - 헬스체크 엔드포인트 기준 운영 상태 지표 정의
- 기준선 수치:
  - `./gradlew test`: 성공, 18초, 5개 Gradle task up-to-date
  - `./gradlew build`: 성공, 3초, 8개 Gradle task up-to-date
  - 테스트 코드 파일: 1개 (`FaithLogApplicationTests.java`)
  - 테스트 리소스 파일: 1개 (`application-test.yml`)
  - DB 마이그레이션: 0개 (Flyway deferred)
- 기획 정합성 보정 수치:
  - 핵심 정책 반영 이슈: 7개 (#21, #27, #28, #31, #38, #39, #40)
  - 수동 `칸반 상태:` 제거 이슈: 21개 범위 검증 중 14개 직접 정리, 최종 잔여 0개
  - 코드 충돌 확인: `refresh_tokens` 테이블/Entity 없음, 현 시점 삭제 작업 불필요
- Project Board 정합성 보정 수치:
  - GitHub Project Board 필드 수정: 24개
  - #39 Priority: P1 -> P0
  - #16 Kanban Status: Code Review -> Done
  - #23~#26 누락된 Priority/Estimate/Work Type/Epic/Release/Domain 필드 보강
  - #23, #24 Domain은 single-select 제약과 혼합 도메인 표기 때문에 pending decision으로 남김
- Codex Hook 세팅 수치:
  - GitHub Issue 생성: 1개 (#43)
  - GitHub Project 카드 연결: 1개
  - Project 카드 상태: In Progress
  - 개발 규칙 문서 추가: 1개 (`docs/codex/FAITHLOG_CODEX_HOOK.md`)
  - README Codex Hook 링크 추가: 1개
  - 금지어 검색 결과: 허용 문서 외 0건
- Agent 규칙 정리 수치:
  - Agent 규칙 파일 단일화: 2개(`AGENT.md`, `AGENTS.md`) -> 1개(`AGENTS.md`)
  - 문서 우선순위 명시: 사용자 최신 결정 > decision-log > Notion > Hook > backend policy > 기존 코드
  - 단수 `optionId` 검사 기준 추가: 1개 명령 패턴
  - `AGENT.md` 활성 로딩 참조: 0건
- 투표 템플릿 정책 정리 수치:
  - 기본 제공 템플릿: 1개(커피)
  - 관리자 생성 템플릿 범주: 3개(수요예배, 토요목자모임, 커스텀)
  - 반영 대상: #37 투표 템플릿/투표 생성 기획
- 투표 자동 생성 정책 정리 수치:
  - 자동 생성 설정 가능 대상: 관리자 생성 PollTemplate
  - 실행 담당 이슈: #24 Scheduler/Batch
  - 중복 방지 기준: campus + template + week 단위 필요
- 커피 투표 시간 설정 정책 수치:
  - 커피 담당자가 설정하는 시간 필드: 2개(자동 생성 시간, 마감 시간)
  - 적용 대상: 기본 커피 PollTemplate
  - 칼럼명 기준: Notion ERD

## Troubleshooting Log

| 날짜 | 문제 | 원인 | 해결 | 전후 수치 | 재발 방지 |
| --- | --- | --- | --- | --- | --- |
| 2026-06-17 | 샌드박스에서 Gradle wrapper lock 파일 접근 실패 | `~/.gradle/wrapper` 락 파일이 샌드박스 쓰기 범위 밖에 있어 `./gradlew test`가 `FileNotFoundException`으로 중단 | 권한 상승으로 동일 명령 재실행 후 성공 | 전: 테스트 실행 실패, 후: `./gradlew test` 21.29초 성공 / `./gradlew build` 7.58초 성공 | 자동화 리포트에서 Gradle 검증은 필요 시 권한 상승 재시도 |
| TBD | TBD | TBD | TBD | TBD | TBD |

## Test Runs

| 날짜 | 명령/방법 | 결과 | 주요 수치 | 후속 조치 |
| --- | --- | --- | --- | --- |
| 2026-06-16 | `./gradlew test` | 성공 | 18초, 5개 task up-to-date, 테스트 통과율 100% | 기능별 테스트 수 확대 |
| 2026-06-16 | `./gradlew build` | 성공 | 3초, 8개 task up-to-date, 빌드 성공률 기준선 100% | 배포 전 빌드 체크 유지 |
| 2026-06-16 | GitHub issue policy audit | 성공 | #17~#41 `칸반 상태:` 잔여 0개, 핵심 이슈 7개 정책 반영 | Project Board 조회에는 `read:project` scope 필요 |
| 2026-06-16 | GitHub Project Board audit | 성공 | Project Board 필드 24개 수정, #39 P0 반영, #16 상태 필드 일치 | #23/#24 Domain 결정 필요 |
| 2026-06-16 | Codex Hook validation | 성공 | `./gradlew test` 13초 성공, 금지어 검색 0건, AGENTS/Hook 문서 존재 확인 | 문서-only 작업이라 신규 테스트 없음 |
| 2026-06-16 | Agent rule consolidation validation | 성공 | `AGENT.md` 활성 로딩 참조 0건, `AGENTS.md` 단일 기준화, 단수 `optionId` 검사 명령 결과 Hook 문서 예시 1건만 확인 | Obsidian 최종 경로 결정 필요 |
| 2026-06-16 | PR readiness validation | 성공 | `./gradlew test` 4초 성공, 5개 task up-to-date | 문서-only PR로 기능 테스트 추가 없음 |
| 2026-06-16 | Poll template planning validation | 성공 | 기본 템플릿 1개, 관리자 생성 템플릿 범주 3개로 정책 확정 | #37 구현 시 seed/admin 생성 테스트 필요 |
| 2026-06-16 | Poll template policy PR validation | 성공 | `./gradlew test` 9초 성공, 5개 task up-to-date | #37 구현 시 기본 커피 템플릿 테스트 필요 |
| 2026-06-16 | Poll auto-generation planning validation | 성공 | #37 설정 저장, #24 스케줄러 실행으로 책임 분리 | 자동 생성 요일/시간/마감 필드 테스트 필요 |
| 2026-06-16 | Poll auto-generation policy PR validation | 성공 | `./gradlew test` 3초 성공, 5개 task up-to-date | #24 구현 시 중복 생성 방지 테스트 필요 |
| 2026-06-16 | Coffee poll timing planning validation | 성공 | 커피 담당자 설정 시간 2개 확정 | #37 구현 시 Notion ERD 칼럼명 확인 필요 |
| 2026-06-16 | Coffee poll timing PR validation | 성공 | `./gradlew test` 4초 성공, 5개 task up-to-date | #37/#24 구현 시 자동 생성/마감 시간 테스트 필요 |
| 2026-06-17 | `./gradlew test` | 성공 | 21.29초, 5개 task up-to-date, 테스트 통과율 100% | 기능 테스트 추가 전까지 smoke baseline 유지 |
| 2026-06-17 | `./gradlew build` | 성공 | 7.58초, 8개 task up-to-date, 빌드 성공률 기준선 100% | 앱 코드 변경 시 build baseline 비교 지속 |
| 2026-06-17 | Repo monitoring audit | 성공 | 워크트리 변경 0건, `develop` 대비 4커밋/6파일/1,179라인 문서 변경, 앱 코드 변경 0개 | 헬스/응답시간 측정 대상 환경 결정 필요 |
| 2026-06-17 | `./gradlew test` 재검증 | 성공 | 31초, 5개 task up-to-date, 테스트 통과율 100% | 현재 브랜치는 문서-only 상태라 기능 테스트 확대 전 smoke baseline 유지 |
| 2026-06-17 | `./gradlew build` 재검증 | 성공 | 8초, 8개 task up-to-date, 빌드 성공률 기준선 100% | 앱 코드 변경이 생기면 오늘 수치와 비교 |
| 2026-06-17 | Local repo structure audit 재검증 | 성공 | 실구현 Java 9개, top-level 모듈 7개, CI workflow 2개, Docker Compose 서비스 5개, 마이그레이션 0개 | 헬스 체크 기준 환경 승인 전까지 운영 지표는 보류 |
| 2026-06-17 | Flyway runtime removal validation | 성공 | `./gradlew test` 35초 성공, `./gradlew build` 26초 성공, `runtimeClasspath` Flyway 항목 0개, active migration file 0개 | 최종 도메인 모델 안정화 후 Flyway migration consolidation task로 재도입 |

## Resume Bullet Candidates

- Spring Boot 기반 FaithLog 프로젝트의 테스트 기준선을 수립하고, `./gradlew test` 기준 테스트 통과율 100%를 확보.
- `./gradlew build` 기준 빌드 성공 상태를 확보해 배포 전 안정성 검증 기준선을 수립.
- FaithLog 백엔드의 일일 모니터링 기준선을 정리해 7개 도메인 모듈, 36개 Java 소스, Flyway deferred 상태, 100% 테스트/빌드 성공 상태를 지속 추적할 수 있게 함.
- GitHub Issues #17~#41의 기획/구현 기준을 최신 백엔드 정책과 정합화하고, 수동 칸반 상태 잔여 0개로 Project Board 중심 운영 기준을 정리.
- GitHub Project Board의 누락/불일치 필드 24개를 정리해 이슈 본문과 칸반 운영 데이터의 정합성을 개선.
- Codex Hook 개발 규칙을 문서화하고 GitHub Issue #43 및 Project 카드와 연결해 TDD/보안/아키텍처/Obsidian 기록 기준을 표준화.
- Codex Agent 규칙 파일을 2개에서 1개로 단일화하고, 사용자 결정 우선순위와 금지 필드 검사 기준을 문서화해 개발 규칙 위반 가능성을 낮춤.
- 투표 템플릿 정책을 기본 제공 1개와 관리자 생성 3개 범주로 분리해 초기 데이터와 운영 권한 기준을 명확화.
- 투표 자동 생성 책임을 템플릿 설정과 스케줄러 실행으로 분리해 반복 운영 자동화 설계 기준을 명확화.
- 커피 담당자가 자동 생성 시간과 마감 시간을 설정하도록 투표 운영 권한과 반복 생성 정책을 구체화.

<!-- daily-resume-monitor:start:resume-metrics:2026-06-16 -->
### 2026-06-16 Automated Resume Monitor

- Evidence source: `docs/prompts/daily-resume-monitor.md` read at runtime.
- Commits reviewed: 4
- Changed files reviewed: 6
- Dependency/config changes reviewed: 0
- DB migration changes reviewed: 0
- Local test result: 1 tests, 0 failures/errors. Measurement method: Gradle XML under `build/test-results/test`. Confidence: verified.
- Build artifacts present locally. Measurement method: `build/libs/*.jar`. Confidence: partially verified.

Metric candidates:
- Health check success rate: measure against a user-approved local or deployed URL with repeated requests.
- API response time: measure with a user-approved endpoint and command so daily values are comparable.
<!-- daily-resume-monitor:end:resume-metrics:2026-06-16 -->
