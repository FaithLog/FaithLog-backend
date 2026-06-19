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
| 품질 | 테스트 통과율 | `./gradlew test` | 100% (2026-06-19, 79 tests / 0 failures) | 100% |
| 품질 | 테스트 코드 파일 수 | `find src/test -type f` | 20 test files (2026-06-18) | 증가 추적 |
| 품질 | 인증/문서 스니펫 묶음 수 | `find build/generated-snippets -mindepth 1 -maxdepth 1 -type d` | 31 snippet groups (2026-06-18) | 증가 추적 |
| 안정성 | 빌드 성공 여부 | `./gradlew build` | 성공 (2026-06-18) | 성공 |
| API | 응답 시간 | 로컬/운영 부하 테스트 | 측정 보류 (2026-06-17) | TBD |
| 운영 | 헬스체크 성공률 | `/health` 또는 배포 플랫폼 상태 | 측정 보류 (2026-06-17) | 99%+ |
| 유지보수 | 주요 모듈 수 | 패키지/도메인 기준 | 7 top-level modules, 157 Java sources (2026-06-18) | 추적 |
| 데이터 | DB 마이그레이션 수 | `src/main/resources/db/migration` | 0 (Flyway deferred, 2026-06-18) | 추적 |

## Daily Monitoring Notes

### 2026-06-19

- #36 PM 재검토 sort 허용 목록 버그 수정:
  - 문제: 관리자 캠퍼스 청구 회원별 집계 조회에서 `sort=amount`, `sort=status`, `sort=paymentCategory` 같은 charge item 전용 정렬 필드가 유효 요청처럼 통과했지만 실제 정렬은 `latestChargeCreatedAt` default로 처리될 수 있었다.
  - TDD 실패 확인: `BillingControllerTest`에 `sort=amount,asc`는 `400 Bad Request`와 `지원하지 않는 정렬 기준입니다.`를 반환하고, `sort=unpaidAmount,desc`는 정상 동작해야 하는 테스트를 먼저 추가. 구현 수정 전 `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest`가 8 tests / 1 failed로 실패.
  - 수정: `BillingPageRequests.adminMembers()` 허용 정렬 목록을 실제 회원 집계 comparator가 지원하는 `createdAt`, `userId`, `name`, `email`, `totalAmount`, `unpaidAmount`, `paidAmount`, `waivedAmount`, `canceledAmount`로 제한. 내 청구/관리자 회원별 상세 charge item 목록 정렬 허용 목록은 유지.
  - 재검증: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(79 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor는 샌드박스 wrapper lock 실패 후 권한 상승 재실행으로 성공.

### 2026-06-18

- #36 청구 목록 조회와 캠퍼스별 집계 구현 검증:
  - 브랜치: `feat/36-charge-list-campus-summary`
  - 구현 API: `GET /api/v1/campuses/{campusId}/charges/me`, `GET /api/v1/campuses/{campusId}/charges/me/summary`, `GET /api/v1/admin/campuses/{campusId}/charges`, `GET /api/v1/admin/campuses/{campusId}/members/{userId}/charges`
  - 구현 모델/서비스: `BillingQueryService`, `ChargeSearchCriteria`, 조회 Query/Result records, 목록 전용 `ChargeListItemResponse`, 관리자 campus/member response DTO
  - 확정 계약: `startDate`/`endDate` 제거, 백엔드는 전체 이력 보존 + pagination/sort/filter 조회, 앱 기본 화면에서 최근 납부 항목 노출 UX 처리. 월별 요약은 `monthlyPaidAmount = paidAt 기준`, `monthlyUnpaidAmount/monthlyTotalChargeAmount/monthlyByCategory = createdAt 기준`.
  - TDD 실패 확인:
    - `./gradlew test --tests com.faithlog.billing.application.BillingQueryServiceTest` 실패: `BillingQueryService`, `MyChargeListQuery`, `MyChargesResult`, admin query/result records 부재로 `compileTestJava` 실패.
    - `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest` 실패: 새 `GET /api/v1/campuses/{campusId}/charges/me` endpoint 미구현으로 기대 200 assertion 실패.
  - 테스트 결과: `./gradlew test --tests com.faithlog.billing.application.BillingQueryServiceTest` 성공, `./gradlew test --tests com.faithlog.billing.presentation.BillingControllerTest` 성공, `./gradlew test --tests com.faithlog.billing.presentation.BillingApiRestDocsTest` 성공, `./gradlew test` 성공(77 tests / 0 failures / 0 errors / 0 skipped)
  - 빌드/문서 결과: `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose build app` 성공, `docker compose up -d postgres redis app` 성공, postgres/redis healthy, app started, 컨테이너 내부 `GET /api/v1/health` 응답 `status=UP` 확인, `docker compose down` 성공. 호스트 `curl localhost:8080`은 현재 세션 네트워크에서 연결 실패했지만 컨테이너 내부 health는 정상.
  - REST Docs 결과: charge query snippets 4개 묶음 추가(`charge-my-list-success`, `charge-my-summary-success`, `charge-admin-campus-summary-success`, `charge-admin-member-detail-success`), 전체 snippet group 31개.
  - 검증 범위: 본인 ACTIVE 캠퍼스 멤버 청구 목록/요약, item `account` snapshot 객체와 `source` 객체, `paymentCategory`/`status` 필터, page/size/sort 기본값, 관리자 campus `summary + members[]` 집계만 반환, 관리자 회원별 상세 대상 회원 `userId/name/email`, 전역 `ADMIN` 허용, 서비스 `MANAGER` 단독 권한 거부, 일반 `MEMBER` 관리자 조회 거부.
  - 코드베이스 수치: Java 소스 157개, 테스트 파일 20개.
  - 금지어 검사: 실제 소스/테스트/API 문서에서 금지어 위반 0건. Swagger 문서화 어노테이션 추가 0건.
  - PM 재검증 보강: 관리자 캠퍼스 청구 조회 `status=UNPAID` 필터 회귀 테스트를 추가해 미납 청구가 있는 회원만 `members[]`에 포함되고, paid/waived/canceled만 있는 회원은 제외되며, 개별 `items`는 반환되지 않는 계약을 고정.
  - PM 재검증 문서화: GitHub Issue #36, Notion `16.1`, `16.4`, `16.5`, `API 설계`, `FaithLog 통합 기획서·ERD·API 설계`를 `startDate`/`endDate` 미사용 정책과 `summary + members`/회원별 상세 분리 기준으로 동기화.
  - PM 재검증 결과: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(78 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor는 샌드박스 wrapper lock 실패 후 권한 상승 재실행으로 성공.

- #35 청구 납부 완료, 면제, 취소 상태 관리 구현 검증:
  - 브랜치: `feat/35-charge-paid-waive-cancel`
  - 구현 API: `PATCH /api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid`, `PATCH /api/v1/admin/charges/{chargeItemId}/status`
  - 구현 모델/서비스: `ChargeItem.markPaid(Instant)`, `ChargeItem.waive`, `ChargeItem.cancel`, `ChargeItem.reopenAsUnpaid`, `BillingService.completeMyChargePayment`, `BillingService.changeChargeStatus`
  - TDD 실패 확인: 구현 전 `./gradlew test --tests 'com.faithlog.billing.*'`가 `CompleteChargePaymentCommand`, `ChangeChargeStatusCommand`, `paidAt`, 상태 전이 메서드 부재로 `compileTestJava` 실패.
  - 테스트 결과: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공, 67 tests / 0 failures / 0 errors / 0 skipped
  - 빌드 결과: `./gradlew build` 성공
  - 문서 렌더 결과: `./gradlew asciidoctor` 성공. 최초 샌드박스 실행은 Gradle wrapper lock 파일 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - REST Docs 결과: charge status snippets 2개 묶음 추가 (`charge-my-paid-success`, `charge-admin-status-change-success`), 전체 snippet group 27개
  - 검증 범위: 본인 `UNPAID` 청구 즉시 `PAID`, `paidAt` 요청값 저장, `paidAt` 생략 시 서버 시간 저장, body 생략/빈 JSON 허용, 타인 청구/다른 캠퍼스/비활성 멤버 거부, terminal 청구 재납부 `409`, 관리자 `UNPAID -> WAIVED/CANCELED`, `PAID/WAIVED/CANCELED -> UNPAID`, `PAID -> UNPAID` 시 `paidAt` null, 관리자 `PAID` 처리 금지, 일반 `MEMBER`와 서비스 `MANAGER` 단독 권한 거부.
  - 코드베이스 수치: Java 소스 134개, 실구현 Java 파일 107개, 테스트 파일 19개.
  - 금지어 검사: 실제 소스/테스트/API 문서에서 금지어 위반 0건. 단수 API 필드 `optionId` 검색은 Hook 문서의 검사 예시 1건만 확인.
  - 범위 분리: 경건생활 자동 청구 연결은 #33, 커피 자동 청구 연결은 #39로 유지.
  - PM 리뷰 테스트 보강:
    - 추가 범위: 전역 `ADMIN`의 캠퍼스 멤버십 없는 관리자 청구 상태 변경 성공, `ELDER`/`CAMPUS_LEADER`의 관리자 청구 상태 변경 성공, `INACTIVE` 멤버의 본인 청구 납부 완료 API `403`.
    - 재검증: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(70 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공.

- #34 계좌와 청구 항목 관리 구현 검증:
  - 브랜치: `feat/34-payment-account-charge-item`
  - 구현 API: `GET /api/v1/campuses/{campusId}/payment-accounts`, `POST /api/v1/admin/campuses/{campusId}/payment-accounts`, `PATCH /api/v1/admin/payment-accounts/{accountId}/deactivate`
  - 구현 모델/서비스: `PaymentAccount`, `ChargeItem`, `PaymentCategory(PENALTY/COFFEE)`, `ChargeSourceType(DEVOTION_RECORD/POLL_RESPONSE)`, `ChargeStatus(UNPAID/PAID/WAIVED/CANCELED)`, `BillingService.createPenaltyCharge`
  - TDD 실패 확인: 구현 전 `./gradlew test --tests 'com.faithlog.billing.*'`가 billing 도메인/서비스/리포지토리 클래스 부재로 `compileTestJava` 실패. terminal 상태 보강 전 `markWaived`, `markCanceled` 부재 실패도 별도 확인.
  - 테스트 결과: `./gradlew test` 성공, 56 tests / 0 failures / 0 errors / 0 skipped
  - 빌드 결과: `./gradlew build` 성공
  - 문서 렌더 결과: `./gradlew asciidoctor` 성공. 최초 샌드박스 실행은 Gradle wrapper lock 파일 권한 문제로 실패했고, 권한 상승 재실행으로 성공.
  - Docker 검증: `docker compose build app` 시도 중 Docker daemon 응답 `Docker Desktop is unable to start`로 중단. 앱 이미지 빌드 전 로컬 Docker Desktop 상태 문제이며 코드 검증으로 완료하지 못함.
  - REST Docs 결과: payment account snippets 3개 묶음 추가 (`payment-account-create-success`, `payment-account-list-success`, `payment-account-deactivate-success`), 전체 snippet group 25개
  - 검증 범위: 캠퍼스별 accountType 활성 계좌 1개 정책, 새 활성 계좌 등록 시 기존 활성 계좌 자동 비활성화, ACTIVE 멤버 조회 허용, 비멤버/INACTIVE 멤버 조회 거부, 계좌 등록/비활성화 관리자 권한, 계좌번호 전체 노출, 일반 멤버 응답의 관리용 메타데이터 미노출, UNPAID 청구 재연결/snapshot 갱신, PAID/WAIVED/CANCELED snapshot 유지, 활성 PENALTY 계좌 누락 시 `관리자에게 문의하세요`, 계좌 누락 시 charge row 미생성, 청구 생성 시 snapshot 저장, 중복 청구 unique 제약.
  - 코드베이스 수치: Java 소스 129개, 실구현 Java 파일 102개, 테스트 파일 19개.
  - 금지어 검사: 실제 소스/README에서 금지어 및 단수 API 필드 `optionId` 위반 0건. Swagger 문서화 어노테이션 추가 0건.
  - 범위 분리: 실제 경건생활 제출 PENALTY 연결은 #33, 커피 투표 응답 COFFEE 연결은 #39로 유지.
  - PM 리뷰 수정:
    - 실패 확인: `./gradlew test --tests 'com.faithlog.billing.*'`가 4건 실패. 서비스 ADMIN 계좌 조회가 `BusinessException`/HTTP assertion failure로 실패했고, 같은 source의 `createPenaltyCharge` 재실행 2건이 unique constraint `DataIntegrityViolationException`으로 실패.
    - 수정 내용: 서비스 `ADMIN`의 계좌 조회 허용, 기존 `UNPAID` PENALTY 청구 create-or-update, 최신 active PENALTY 계좌 snapshot/title/reason/amount/dueDate 갱신. terminal 청구 재제출 정책은 아직 사용자 확정 전이므로 #34 foundation에서는 데이터 훼손 방지용 구현 가드로 덮어쓰기를 막고 명확한 예외를 반환.
    - 재검증: `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(59 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공.
    - Docker 재검증: `docker compose build app`가 Docker daemon 응답 `Docker Desktop is unable to start`로 동일하게 중단.

- #30 캠퍼스 멤버 역할/커피 담당자 관리 구현 검증:
  - 브랜치: `feat/30-campus-member-role-duty-assignment`
  - 구현 API: `GET /api/v1/admin/campuses/{campusId}/members`, `PATCH /api/v1/admin/campuses/{campusId}/members/{campusMemberId}/campus-role`, `GET /api/v1/admin/campuses/{campusId}/duty-assignments`, `PUT /api/v1/admin/campuses/{campusId}/duty-assignments/coffee`, `DELETE /api/v1/admin/campuses/{campusId}/duty-assignments/coffee/{assignmentId}`
  - 테스트 결과: `./gradlew test` 성공, 47 tests / 0 failures / 0 errors / 0 skipped
  - 빌드 결과: `./gradlew build` 성공
  - REST Docs 결과: `./gradlew asciidoctor` 성공, Spring REST Docs snippet group 22개 생성, admin campus snippets 5개 추가
  - 역할 변경 검증: `MINISTER`/`ELDER`/`CAMPUS_LEADER`의 same-level assignment, 상위 역할 변경/부여 거부, `MEMBER` 권한 없음, 전역 `ADMIN` 전체 변경 허용, 전역 `MANAGER` 단독 권한 없음, 마지막 관리 역할 `MEMBER` downgrade 허용.
  - 커피 담당자 검증: `CampusDutyAssignment + DutyType.COFFEE`로 분리, 새 담당자 지정 시 기존 활성 담당자 inactive 처리, 활성 담당자 목록 1명 유지, 해제 시 inactive/revoked 처리, non-`MEMBER` 캠퍼스 역할 및 전역 `ADMIN` 관리 허용.
  - 동시성 보강: concurrent `PUT /duty-assignments/coffee` 상황을 재현하는 `CampusDutyAssignmentConcurrencyTest` 추가. 구현 전에는 active row 중복으로 `NonUniqueResultException` 실패를 확인했고, 캠퍼스 row `PESSIMISTIC_WRITE` lock 적용 후 동시 12개 지정 요청에서도 active coffee assignment 1개 유지 검증.
  - 코드베이스 수치: Java 소스 110개, 실구현 Java 파일 83개, 테스트 소스 14개, 테스트 리소스 1개.
  - Docker 검증: `docker compose build app` 성공, `docker compose up -d postgres redis app` 성공, postgres/redis healthy, app container started, `GET /api/v1/health` 응답 `status=UP` 확인.
  - 문서 동기화: `docs/codex/FAITHLOG_CODEX_HOOK.md`, `docs/backend-implementation-policy.md`, GitHub Issue #30, Notion role/coffee/API 통합 문서를 same-level assignment 기준으로 갱신.
  - 금지어 검사: 실제 소스/테스트/API 문서에서 금지어 및 단수 API 필드 `optionId` 위반 0건. 허용된 정책 문서 예시만 검색됨.
  - 제품/아키텍처 결정 기록: same-level campus role assignment와 coffee duty non-`MEMBER` permission을 `docs/decision-log.md`에 기록.

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
| 2026-06-18 | #36 TDD 실패 확인 | 실패 확인 | Query service 테스트는 missing class 15개로 `compileTestJava` 실패, Controller 테스트는 새 조회 endpoint 미구현으로 HTTP 200 assertion 실패 | 조회 Query Service, Result/Response DTO, Controller endpoint 구현 |
| 2026-06-18 | #36 focused query/controller/docs tests | 성공 | `BillingQueryServiceTest`, `BillingControllerTest`, `BillingApiRestDocsTest` 각각 성공 | 전체 테스트로 확대 |
| 2026-06-18 | #36 full regression/build/docs/docker | 성공 | `./gradlew test` 성공, 77 tests / 0 failures / 0 errors / 0 skipped; `./gradlew build` 성공; `./gradlew asciidoctor` 성공; Docker compose app 내부 health `UP` | PM 리뷰 전 브랜치 push 여부 확인 필요 |
| 2026-06-18 | #36 PM revalidation unpaid filter/docs sync | 성공 | 관리자 캠퍼스 조회 `status=UNPAID` 회귀 테스트 추가. 구현 변경 전 `BillingControllerTest` 성공으로 기존 동작이 계약을 이미 만족함을 확인. 최종 `./gradlew test` 성공, 78 tests / 0 failures / 0 errors / 0 skipped; `./gradlew build` 성공; `./gradlew asciidoctor` 성공 | PM 재검토 후 push/PR 여부 확인 필요 |
| 2026-06-19 | #36 PM re-review admin member summary sort guard | 성공 | 구현 수정 전 `BillingControllerTest` 실패 확인(8 tests / 1 failed). 수정 후 `./gradlew test --tests 'com.faithlog.billing.*'` 성공, `./gradlew test` 성공(79 tests / 0 failures / 0 errors / 0 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공 | PM 재검토 후 push/PR 여부 확인 필요 |
| 2026-06-18 | #35 PM review permission regression tests | 성공 | 전역 `ADMIN` 무멤버십 상태 변경, `ELDER`/`CAMPUS_LEADER` 상태 변경, `INACTIVE` 멤버 본인 납부 `403` 테스트 추가 | PR 전 권한 회귀 테스트 유지 |
| 2026-06-18 | #35 PM review focused tests | 성공 | `./gradlew test --tests 'com.faithlog.billing.*'` 성공 | 전체 테스트로 확대 |
| 2026-06-18 | #35 PM review full regression/build | 성공 | `./gradlew test` 성공, 70 tests / 0 failures / 0 errors / 0 skipped; `./gradlew build` 성공 | PR 생성 전 최종 확인 |
| 2026-06-18 | #35 TDD 실패 확인 | 실패 확인 | `./gradlew test --tests 'com.faithlog.billing.*'` 실패: `CompleteChargePaymentCommand`, `ChangeChargeStatusCommand`, `paidAt`, 상태 전이 메서드 부재로 `compileTestJava` 실패 | 청구 상태 전이 도메인/서비스/API 최소 구현 |
| 2026-06-18 | #35 billing 집중 테스트 | 성공 | `./gradlew test --tests 'com.faithlog.billing.*'` 성공, billing 테스트 20개 통과 | 전체 테스트로 확대 |
| 2026-06-18 | #35 full regression | 성공 | `./gradlew test` 성공, 67 tests / 0 failures / 0 errors / 0 skipped | build/asciidoctor 확인 |
| 2026-06-18 | #35 build/docs | 성공 | `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패 후 권한 상승 재실행 성공 | PR 전 동일 검증 유지 |
| 2026-06-18 | `./gradlew test --rerun-tasks` | 성공 | 20초, 5 tasks executed, 21 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | `--warning-mode all`로 deprecated feature 원인 식별 필요 |
| 2026-06-18 | #30 TDD 실패 확인 | 실패 확인 | `./gradlew test --tests com.faithlog.campus.application.CampusServiceTest --tests com.faithlog.campus.presentation.CampusControllerTest --tests com.faithlog.campus.presentation.CampusApiRestDocsTest`가 구현 전 `DutyType`, `ChangeCampusRoleCommand`, `AssignCoffeeDutyCommand`, `DutyAssignmentResult`, 서비스 메서드 부재로 `compileTestJava` 실패 | 최소 구현 후 동일 테스트 통과 |
| 2026-06-18 | #30 커피 담당자 동시 지정 실패 확인 | 실패 확인 | `./gradlew test --tests com.faithlog.campus.application.CampusDutyAssignmentConcurrencyTest`가 구현 전 active row 중복으로 `NonUniqueResultException` 실패 | 캠퍼스 row pessimistic lock 적용 후 동일 테스트 통과 |
| 2026-06-18 | #30 집중 테스트 | 성공 | `CampusServiceTest`, `CampusControllerTest`, `CampusApiRestDocsTest` 통과 | 전체 테스트로 확대 |
| 2026-06-18 | #30 `./gradlew test` | 성공 | 47 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | Docker 검증 완료 |
| 2026-06-18 | #30 `./gradlew build` | 성공 | bootJar/build 성공, Gradle deprecated feature 경고 유지 | Gradle 9 호환성 경고는 별도 정리 |
| 2026-06-18 | #30 `./gradlew asciidoctor` | 성공 | REST Docs snippet group 22개, admin campus snippets 5개 추가, 샌드박스 Gradle wrapper lock 실패 후 권한 상승 재실행 성공 | 신규 관리자 API마다 REST Docs 테스트 유지 |
| 2026-06-18 | #30 Docker validation | 성공 | `docker compose build app` 성공, `docker compose up -d postgres redis app` 성공, postgres/redis healthy, app started, `curl http://localhost:8080/api/v1/health` 성공 | 컨테이너는 검증 후 실행 상태 유지 |
| 2026-06-18 | #34 TDD 실패 확인 | 실패 확인 | 구현 전 billing 도메인/서비스/리포지토리 부재로 `./gradlew test --tests 'com.faithlog.billing.*'` `compileTestJava` 실패. terminal 상태 보강 전 `markWaived`, `markCanceled` 부재 실패 확인 | 최소 구현 및 terminal 상태 전이 추가 후 동일 범위 통과 |
| 2026-06-18 | #34 billing 집중 테스트 | 성공 | `./gradlew test --tests 'com.faithlog.billing.*'` 성공, billing service/controller/REST Docs 테스트 통과 | 전체 테스트로 확대 |
| 2026-06-18 | #34 `./gradlew test` | 성공 | 56 tests / 0 failures / 0 errors / 0 skipped, 테스트 통과율 100% | `./gradlew build`, `asciidoctor` 추가 확인 |
| 2026-06-18 | #34 `./gradlew build` | 성공 | bootJar/build 성공, Gradle deprecated feature 경고 유지 | Docker 검증 시도 |
| 2026-06-18 | #34 `./gradlew asciidoctor` | 성공 | payment account snippets 3개 묶음, 전체 snippet group 25개, 샌드박스 Gradle wrapper lock 실패 후 권한 상승 재실행 성공 | 신규 billing API 문서 include 유지 |
| 2026-06-18 | #34 Docker validation | 실패 | `docker compose build app`가 Docker daemon 응답 `Docker Desktop is unable to start`로 중단 | Docker Desktop 실행 가능 상태에서 재시도 필요 |
| 2026-06-18 | #34 PM review failure tests | 실패 확인 | `./gradlew test --tests 'com.faithlog.billing.*'` 실패: service ADMIN 계좌 조회 403 계열 실패, HTTP ADMIN 조회 assertion 실패, penalty charge rerun 2건 unique constraint 실패 | 관리자 조회 권한과 UNPAID 청구 create-or-update 구현 |
| 2026-06-18 | #34 PM review focused tests | 성공 | `./gradlew test --tests 'com.faithlog.billing.*'` 성공, billing 테스트 12개 통과 | 전체 테스트로 확대 |
| 2026-06-18 | #34 PM review full regression | 성공 | `./gradlew test` 성공, 59 tests / 0 failures / 0 errors / 0 skipped | build/asciidoctor 확인 |
| 2026-06-18 | #34 PM review build/docs | 성공 | `./gradlew build` 성공, `./gradlew asciidoctor` 성공. asciidoctor 최초 샌드박스 실행은 Gradle wrapper lock 권한 문제로 실패 후 권한 상승 재실행 성공 | Docker Desktop 상태 복구 후 Docker 재검증 필요 |
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
