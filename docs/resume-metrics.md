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
| 품질 | 테스트 통과율 | `./gradlew test` | 100% (2026-06-18, 37 tests / 0 failures) | 100% |
| 품질 | 테스트 코드 파일 수 | `find src/test -type f` | 14 test sources, 1 test resource (2026-06-18) | 증가 추적 |
| 품질 | 인증/문서 스니펫 묶음 수 | `find build/generated-snippets -mindepth 1 -maxdepth 1 -type d` | 17 snippet groups (2026-06-18) | 증가 추적 |
| 안정성 | 빌드 성공 여부 | `./gradlew build` | 성공 (2026-06-18) | 성공 |
| API | 응답 시간 | 로컬/운영 부하 테스트 | 측정 보류 (2026-06-17) | TBD |
| 운영 | 헬스체크 성공률 | `/health` 또는 배포 플랫폼 상태 | 측정 보류 (2026-06-17) | 99%+ |
| 유지보수 | 주요 모듈 수 | 패키지/도메인 기준 | 7 top-level modules (2026-06-18) | 추적 |
| 데이터 | DB 마이그레이션 수 | `src/main/resources/db/migration` | 0 (Flyway deferred, 2026-06-18) | 추적 |

## Daily Monitoring Notes

### 2026-06-18

- #29 캠퍼스 생성/초대코드 가입 구현 검증:
  - 브랜치: `feat/29-campus-create-join`
  - 구현 API: `POST /api/v1/campuses`, `POST /api/v1/campuses/join`, `GET /api/v1/campuses/me`, `GET /api/v1/campuses/{campusId}`, `DELETE /api/v1/campuses/{campusId}/members/{membershipId}`
  - 테스트 결과: `./gradlew test` 성공, 37 tests / 0 failures / 0 errors / 0 skipped
  - 빌드 결과: `./gradlew build` 성공
  - REST Docs 결과: `./gradlew asciidoctor` 성공, Spring REST Docs snippet group 17개 생성
  - 캠퍼스 관련 신규/변경 테스트 파일: 3개 (`CampusControllerTest`, `CampusServiceTest`, `CampusApiRestDocsTest`)
  - 캠퍼스 멤버 삭제 검증: 일반 `MEMBER` 삭제 거부, `ELDER` 삭제 허용, 서비스 `ADMIN` 캠퍼스 미가입 삭제 허용, 삭제 시 `INACTIVE` 전이, 삭제 후 초대코드 재가입 시 기존 멤버십 `ACTIVE + MEMBER` 재활성화.
  - Java 소스 수: 96개, 실구현 Java 파일 69개, `package-info.java` 27개
  - Docker PostgreSQL 검증: `docker compose up -d postgres redis app` 이미지 빌드와 postgres/redis healthcheck 성공. 기존 로컬 Docker volume의 `faithlog` role 비밀번호가 compose 네트워크 접속 기준과 어긋난 상태를 `ALTER USER faithlog WITH PASSWORD 'faithlog'`로 정리한 뒤, `docker compose up -d --force-recreate app` 및 `GET /api/v1/health` 200 검증 완료.
  - 금지어 검사: 실제 소스/테스트/API 문서에서 금지어 및 단수 API 필드 `optionId` 위반 0건. `CampusRole` 검사 결과는 최종 enum 값 `MINISTER`, `ELDER`, `CAMPUS_LEADER`, `MEMBER` 구조로 정상.
  - 제품/아키텍처 결정 기록: 캠퍼스 생성은 `PaymentAccount`와 `penalty_rules`를 만들지 않으며, `GET /campuses/me`와 상세 조회 응답 계약/오류 메시지를 `docs/decision-log.md`에 기록.

- 브랜치/작업트리:
  - 현재 브랜치: `feat/28-auth-refresh-logout-redis`
  - `git status --short --branch`: `docs/resume-metrics.md`와 wiki 문서 변경/추가가 남아 있던 상태에서 검증했고, 이 중 `docs/resume-metrics.md`는 별도 docs 커밋 대상으로 정리
  - `develop` 대비 추가 커밋: 6개 (`0b7cc7a`, `f14ffb7`, `ea5bd3d`, `59d89b0`, `3885808`, `9a910ba`)
- 변경 범위 수치:
  - `git diff --stat origin/develop..HEAD`: 33 files changed, 1,099 insertions, 12 deletions
  - 앱 코드 변경 파일: 22개
  - 테스트 코드 변경 파일: 7개
  - 프로젝트 문서 변경 파일: 3개 (`docs/backend-implementation-policy.md`, `docs/codex/FAITHLOG_CODEX_HOOK.md`, `docs/decision-log.md`)
  - API 문서 변경 파일: 1개 (`src/docs/asciidoc/index.adoc`)
  - 변경 모듈: 2개 (`global`, `user`)
  - 의존성/DB 마이그레이션 변경: 0건
- 코드베이스 구조 수치:
  - `src/main/java/com/faithlog` top-level 모듈: 7개 (`billing`, `campus`, `devotion`, `global`, `notification`, `poll`, `user`)
  - Java 소스 파일: 74개
  - 실구현 Java 파일(`package-info.java` 제외): 47개
  - `package-info.java`: 27개
  - 테스트 소스 파일: 11개
  - 테스트 리소스 파일: 1개
  - 테스트 스위트: 7개
  - 테스트 케이스: 21개
  - DB 마이그레이션 파일: 0개 (Flyway deferred)
- 검증 신호:
  - `./gradlew test --rerun-tasks`: 성공, 20초, 5 tasks executed, 21 tests / 0 failures / 0 errors / 0 skipped
  - `./gradlew asciidoctor --rerun-tasks`: 성공, 39초, 6 tasks executed, REST Docs snippets 재생성
  - `./gradlew build`: 성공, 5초, 3 tasks executed / 5 tasks up-to-date
  - 빌드 아티팩트: `build/libs/faithlog-backend-0.0.1-SNAPSHOT.jar`, `build/libs/faithlog-backend-0.0.1-SNAPSHOT-plain.jar`
  - Spring REST Docs 스니펫 묶음: 10개
  - 생성 문서 확인: `build/docs/asciidoc/index.html` 91,078 bytes
- 운영/문서 신호:
  - 인증 상세 계약 문서 파일 유지: `src/docs/asciidoc/index.adoc`
  - 코드상 헬스 엔드포인트 존재: `GET /api/v1/health`
  - 응답 시간/헬스 성공률은 측정 대상 환경이 승인되지 않아 오늘도 수치 미기록
- 오늘 리스크/관찰:
  - Gradle 실행은 성공했지만 deprecated feature 경고가 남아 있어 Gradle 9 업그레이드 전에 원인 정리가 필요하다.
  - 현재 브랜치 변경은 인증(`user`, `global`)에 집중되어 있고 나머지 5개 top-level 모듈은 이번 브랜치에서 직접 변경되지 않았다.
  - #40의 실제 FCM port 구현이 추가될 때 NoOp FCM adapter가 충돌하거나 우선 사용되지 않도록 `@ConditionalOnMissingBean(CurrentDeviceFcmTokenDeactivationPort.class)` 기반 configuration bean으로 정리했다.
- 오늘 테스트 후보:
  - `./gradlew test --warning-mode all`
  - 이유: 오늘 `test`/`build` 모두 Gradle deprecated feature 경고를 출력했지만 원인 플러그인/스크립트가 식별되지 않았다.
  - 기대 지표: 경고 발생 항목 수, 소유 위치(빌드 스크립트/플러그인), Gradle 9 호환성 정리 backlog
  - `docker compose up -d postgres redis app` 후 `curl /api/v1/health` 반복 측정
  - 이유: 헬스 엔드포인트는 존재하지만 승인된 일일 측정 대상이 아직 없다.
  - 기대 지표: 앱 기동 성공 여부, HTTP 200 여부, 응답 시간(ms), 연속 성공률(%)

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
| 2026-06-18 | 샌드박스에서 `./gradlew asciidoctor` 실행 실패 | `~/.gradle/wrapper` 락 파일이 샌드박스 쓰기 범위 밖에 있어 Gradle wrapper가 `.zip.lck` 파일을 열지 못함 | 권한 상승으로 동일 명령 재실행 후 성공 | 전: `./gradlew asciidoctor` 즉시 실패, 후: 3초 성공 + `build/docs/asciidoc/index.html` 생성 확인 | Gradle 기반 문서 생성 검증은 샌드박스 실패 시 권한 상승 재시도 |
| 2026-06-17 | 샌드박스에서 Gradle wrapper lock 파일 접근 실패 | `~/.gradle/wrapper` 락 파일이 샌드박스 쓰기 범위 밖에 있어 `./gradlew test`가 `FileNotFoundException`으로 중단 | 권한 상승으로 동일 명령 재실행 후 성공 | 전: 테스트 실행 실패, 후: `./gradlew test` 21.29초 성공 / `./gradlew build` 7.58초 성공 | 자동화 리포트에서 Gradle 검증은 필요 시 권한 상승 재시도 |
| TBD | TBD | TBD | TBD | TBD | TBD |

## Test Runs

| 날짜 | 명령/방법 | 결과 | 주요 수치 | 후속 조치 |
| --- | --- | --- | --- | --- |
| 2026-06-18 | `./gradlew test --rerun-tasks` | 성공 | 20초, 5 tasks executed, 21 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | `--warning-mode all`로 deprecated feature 원인 식별 필요 |
| 2026-06-18 | #29 `./gradlew test` | 성공 | 31 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | 캠퍼스 멤버 관리 이슈에서 권한 테스트 추가 |
| 2026-06-18 | #29 `./gradlew build` | 성공 | bootJar/build 성공, Gradle deprecated feature 경고 유지 | Gradle 9 호환성 경고는 별도 정리 |
| 2026-06-18 | #29 `./gradlew asciidoctor` | 성공 | REST Docs snippet group 16개, 캠퍼스 API snippets 6개 추가 | 신규 API마다 REST Docs 테스트 유지 |
| 2026-06-18 | #29 Docker PostgreSQL validation | 성공 | `docker compose up -d postgres redis app` 이미지 빌드 성공, postgres/redis healthy, 기존 로컬 Docker volume의 `faithlog` role password 재설정 후 app 기동 및 `GET /api/v1/health` 200 확인 | Docker volume을 삭제하지 않고 credential mismatch를 복구하는 절차를 troubleshooting에 유지 |
| 2026-06-18 | `./gradlew asciidoctor --rerun-tasks` | 성공 | 39초, 6 tasks executed, REST Docs 스니펫 10개, HTML 1개 생성 | 새 API 추가 시 문서 스니펫 수와 HTML 생성 여부 비교 |
| 2026-06-18 | `./gradlew build` | 성공 | 5초, 3 tasks executed / 5 tasks up-to-date, 빌드 성공률 기준선 100%, JAR 2개 유지 | 앱 코드 변경이 생기면 오늘 수치와 비교 |
| 2026-06-18 | Branch monitoring audit | 성공 | `origin/develop` 대비 6커밋, 33파일, +1,099/-12, 앱 코드 22파일, 테스트 코드 7파일, DB migration 0개 | 헬스/응답시간 측정 대상 환경 결정 필요 |
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
| 2026-06-17 | #27 auth JWT TDD validation | 성공 | `./gradlew test` 18초 성공, 테스트 파일 1개 -> 4개, 회원가입/로그인/JWT claim/Bearer `/users/me` 검증 추가 | #28 refresh/logout/Redis rotation 구현 시 인증 테스트 확장 |
| 2026-06-17 | #27 PM review security fix validation | 성공 | `./gradlew test` 16초 성공, `./gradlew build` 5초 성공, refresh token Bearer 인증 401 테스트 추가, 비활성 사용자 `/users/me` 401 테스트 추가 | #28 Redis allowlist/blacklist 구현 시 tokenType 검증 유지 |
| 2026-06-17 | #27 Docker validation | 부분 성공 | `docker compose build` 성공, `docker compose up -d postgres redis app` 후 postgres/redis healthy, app은 기존 Postgres volume credential mismatch로 `FATAL: password authentication failed for user "faithlog"` 종료 | Docker volume credential 정리 또는 승인된 DB 초기화 후 앱 헬스체크 재검증 |
| 2026-06-17 | #27 Docker local ddl-auto update validation | 성공 | `docker compose build app` 성공, `docker compose up -d app` 성공, `GET /api/v1/health` 200, Hibernate가 local Docker DB에 `users` 테이블 자동 생성 | 최종 Flyway migration consolidation 전까지 local Docker 개발 검증은 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 기본값 사용 |
| 2026-06-17 | #27 auth REST Docs validation | 성공 | `./gradlew test --tests '*AuthApiRestDocsTest'` 성공, `./gradlew asciidoctor` 성공, `./gradlew test --rerun-tasks` 11초 성공, `./gradlew build` 5초 성공, 인증 API snippets 6개 묶음과 `build/docs/asciidoc/index.html` 생성 | 새 API/변경 API는 Spring REST Docs 테스트로 상세 계약 문서화 |
| 2026-06-17 | #27 CI test profile override fix | 성공 | PR #47 Backend CI 실패 원인 확인, CI env 재현 실패 확인, 수정 후 `./gradlew test --tests '*AuthServiceTest'` 성공, `./gradlew test --rerun-tasks` 11초 성공, `./gradlew build` 2초 성공 | GitHub Actions 재실행 후 원격 check 통과 확인 |

## Resume Bullet Candidates

- Spring Boot 기반 FaithLog 프로젝트의 테스트 기준선을 수립하고, `./gradlew test` 기준 테스트 통과율 100%를 확보.
- `./gradlew build` 기준 빌드 성공 상태를 확보해 배포 전 안정성 검증 기준선을 수립.
- FaithLog 백엔드의 일일 모니터링 기준선을 정리해 7개 도메인 모듈, 74개 Java 소스, Flyway deferred 상태, 100% 테스트/빌드 성공 상태를 지속 추적할 수 있게 함.
- GitHub Issues #17~#41의 기획/구현 기준을 최신 백엔드 정책과 정합화하고, 수동 칸반 상태 잔여 0개로 Project Board 중심 운영 기준을 정리.
- GitHub Project Board의 누락/불일치 필드 24개를 정리해 이슈 본문과 칸반 운영 데이터의 정합성을 개선.
- Codex Hook 개발 규칙을 문서화하고 GitHub Issue #43 및 Project 카드와 연결해 TDD/보안/아키텍처/Obsidian 기록 기준을 표준화.
- Codex Agent 규칙 파일을 2개에서 1개로 단일화하고, 사용자 결정 우선순위와 금지 필드 검사 기준을 문서화해 개발 규칙 위반 가능성을 낮춤.
- 투표 템플릿 정책을 기본 제공 1개와 관리자 생성 3개 범주로 분리해 초기 데이터와 운영 권한 기준을 명확화.
- 투표 자동 생성 책임을 템플릿 설정과 스케줄러 실행으로 분리해 반복 운영 자동화 설계 기준을 명확화.
- 커피 담당자가 자동 생성 시간과 마감 시간을 설정하도록 투표 운영 권한과 반복 생성 정책을 구체화.
- 회원가입/로그인/JWT 인증 흐름을 TDD로 구현하고, Bearer 인증 `/api/v1/users/me`와 JWT 필수 claim 검증을 포함한 테스트 파일을 1개에서 4개로 확대.
- Swagger/springdoc은 API 탐색용으로 유지하면서, 회원가입/로그인/내 정보 조회의 상세 계약을 Spring REST Docs 문서 생성 테스트로 검증하도록 확장.
- Redis allowlist/blacklist 기반 refresh/logout 흐름을 추가하고, 인증 테스트 스위트를 7개·21개 케이스까지 확장해 토큰 회전과 로그아웃 계약을 검증.
- 인증 문서화를 Spring REST Docs 중심으로 유지하면서 스니펫 묶음 10개와 AsciiDoc 계약 문서를 계속 생성 가능한 상태로 검증.
- 캠퍼스 생성/초대코드 가입 API 4개를 TDD로 구현하고, 테스트 스위트를 31개 케이스와 Spring REST Docs 스니펫 16개 묶음까지 확장해 권한/멤버십/초대코드 노출 계약을 검증.

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

<!-- daily-resume-monitor:start:resume-metrics:2026-06-17 -->
### 2026-06-17 Automated Resume Monitor

- Evidence source: `docs/prompts/daily-resume-monitor.md` read at runtime.
- Commits reviewed: 5
- Changed files reviewed: 32
- Dependency/config changes reviewed: 0
- DB migration changes reviewed: 0
- Local test result: 21 tests, 0 failures/errors/skips. Measurement method: Gradle XML under `build/test-results/test`. Confidence: verified.
- Local build result: `./gradlew build` success in 25s with 8 up-to-date tasks. Confidence: verified.
- API contract docs: `./gradlew asciidoctor` success in 3s, 10 snippet directories, `build/docs/asciidoc/index.html` present. Confidence: verified.

Metric candidates:
- Health check success rate: run `docker compose up -d postgres redis app` and repeat `curl /api/v1/health` against one approved runtime.
- API response time: measure `GET /api/v1/health` or another user-approved endpoint with a fixed local or deployed target.
<!-- daily-resume-monitor:end:resume-metrics:2026-06-17 -->
