# 2026-08-13 목장 담당자 및 주간 목홀타 집계

## 작업 배경

- Issue #260은 카카오톡과 수기로 취합하던 주일 목장별 `목장모임`, `홀리웨이브`, `타예배` 인원을 서버의 구조화된 주간 집계로 전환하는 작업이다.

## 구현 기준

- 목장은 campus scope이며 일반 ACTIVE 멤버 생성 시 본인 자동 담당이다.
- 캠퍼스 관리자와 서비스 ADMIN은 같은 캠퍼스 ACTIVE 사용자 중 담당자를 1명 이상 지정하고 교체할 수 있다.
- `serviceDate`는 Sunday, count 3종은 각각 0 이상이며 `DRAFT`/`SUBMITTED` 모두 수정 가능하다.
- stale write는 응답의 `version`을 저장 요청에 다시 보내는 방식으로 `409 SHEPHERD_ATTENDANCE_CONFLICT` 처리한다.
- 일반 사용자 홈 카드는 `Asia/Seoul` 기준 일요일 00:00:00 이상 월요일 00:00:00 미만에만 노출 후보이며, 현재 ACTIVE campus의 담당 ACTIVE 목장이 1개 이상 있어야 한다.
- 홈 카드 문구는 `이번 주 목홀타를 입력해 주세요`이고 담당 목장 수, 현재 Sunday `SUBMITTED` 완료 수, 담당 목장별 report-or-null rows를 반환한다. 비일요일과 미담당 사용자는 `visible=false`, `groups=[]`다.

## 구현 내용

- Entity: `shepherd_groups`, `shepherd_group_assignees`, `weekly_shepherd_attendance_reports`
- Service: `ShepherdService`
- Repository: 관리자 board page projection, assignee bulk projection, SUBMITTED 기준 campus aggregate projection, 홈 카드 담당 목장+현재 Sunday report bulk projection
- API: 일반 생성/내 목록/보고서 조회·저장/홈 카드, 관리자 그룹 목록/수정/담당자 교체/board/대리 저장
- Migration: `V23__add_shepherd_attendance.sql`

## TDD 기록

1. RED: `ShepherdServiceTest`를 먼저 추가하고 `compileTestJava` 43 errors를 확인했다.
2. GREEN: domain/service/repository/migration 구현 뒤 focused service 7 tests 통과.
3. REST Docs: MockMvc 기반 API snippets 생성 테스트 통과.
4. N+1: 1/100/1000 목장 관리자 board prepared statement count 4 고정 확인.
5. 추가 RED: 최신 홈 카드 계약 반영 후 `ShepherdHomeCardResult`/`getMyHome` 부재로 `compileTestJava` 11 errors 확인.
6. 추가 GREEN: server Clock + Asia/Seoul, 홈 bulk projection, 전용 API/DTO/REST Docs 구현 뒤 service/docs 12 tests 통과.
7. Self-review RED: 관리자 board 완료 수가 DRAFT까지 세는 문제를 실행형 테스트로 재현하고, SUBMITTED만 완료로 집계하도록 수정.

## 검증

- `./gradlew test --tests com.faithlog.shepherd.service.ShepherdServiceTest`: BUILD SUCCESSFUL
- `./gradlew test --tests com.faithlog.shepherd.controller.ShepherdApiRestDocsTest`: BUILD SUCCESSFUL
- `./gradlew test --tests com.faithlog.shepherd.service.ShepherdServiceTest --tests com.faithlog.shepherd.controller.ShepherdApiRestDocsTest`: 12 tests, failures 0
- focused shepherd + Flyway static + architecture 묶음: BUILD SUCCESSFUL
- final disposable PostgreSQL 17 `faithlog-260-pg-final-20260813` / port `55461` `PostgresFlywayMigrationTest`: 14 tests, failures 0, errors 0, skipped 0

## 남은 확인

- 전체 `./gradlew test build asciidoctor`
