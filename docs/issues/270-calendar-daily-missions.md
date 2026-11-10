# #270 캘린더 저장 기반 일간 보상

사용자 승인: 캘린더 저장 성공 뒤 오늘 큐티·기도·말씀을 각각 30P, 하루 한 번 수령한다. 주간 제출 보상은 별도다. 기존 주간 저장은 일간 미션의 저장 근거가 아니다.

TDD: 실패 테스트를 먼저 작성했으나 Gradle 배포판 다운로드 단계에서 Network is unreachable로 중단되어 RED/GREEN 실행을 확인할 수 없음. 테스트 통과로 보고하지 않는다.

범위: 서버 저장 근거·수령 원장과 앱 연결. 정식 뽑기 경제 전체, 스토어 공개, 운영 DB 마이그레이션 실행 제외. GitHub Projects 도구가 없어 보드 갱신 불가. 로컬 환경에 사용자 Obsidian Vault가 없어 이 문서와 decision-log에 기록한다.

API: GET /api/v1/campuses/{campusId}/devotions/me/daily-missions, POST /{mission}/claims. mission은 QUIET_TIME/PRAYER/BIBLE. 날짜·금액은 요청으로 받지 않고 서버가 결정한다. 미저장/해제 상태는 409 DEVOTION_MISSION_NOT_READY. 이미 수령한 요청은 200으로 같은 누적액을 반환한다.

스키마: V24 calendar_daily_rewards, (campus_id,user_id,record_date,mission) UNIQUE. calendar_checked와 claimed_at/points를 분리해 재저장으로 수령 이력이 초기화되지 않는다. JPA entity를 Controller에서 반환하지 않는다. 신규 의존성 없음.

검증 환경 복구: 기본 환경은 JRE만 있어 JDK21을 임시 설치했고, 플랫폼 인증서 truststore와 제공된 네트워크 proxy 설정을 사용한다. 인증서 검증을 비활성화하지 않는다. 초기 Gradle 네트워크 오류를 해결한 뒤 실제 테스트 결과를 별도로 기록한다.

## PM review

독립 검토 1건(P2): 기기 날짜와 서버 미션 날짜가 다를 때 다른 캘린더 날짜가 열릴 수 있음. 프론트가 서버 recordDate를 이동 대상에 전달하도록 수정하고 회귀 테스트 추가. 재검토에서 남은 finding 0건.

현재 검증: backend focused 56 tests 통과(도메인 2 + 저장/수령/롤백/동시성/캠퍼스 격리 통합 5 포함). frontend 전체 회귀와 타입 검사·린트 통과. 전체 backend test/build/asciidoctor 실행 중이며 최종 결과는 아래에 추가한다. Docker 실행 파일이 없어 PostgreSQL Flyway 실제 실행과 Docker QA는 수행하지 않음. 운영 배포 전 통합 환경에서 V24 확인 필요.

권한/트랜잭션/회귀: 기존 인증 필터와 활성 캠퍼스 검증 재사용. 일별 저장의 반환 계약과 주간 제출/벌금 의미는 유지. 신규 의존성 없음. 날짜·금액은 클라이언트에서 받지 않음. 보상 수령은 저장 근거와 현재 체크를 재검증하며 회원 범위 write lock과 scope UNIQUE로 직렬화한다.

최종 게이트: `./gradlew --no-daemon test build asciidoctor` BUILD SUCCESSFUL (JDK21, 시스템 truststore, proxy, 명시적 ByteBuddy 테스트 agent 환경). classes=211, tests=1025, failures=0, errors=0, skipped=24. REST Docs daily-mission-get/claim/unsaved snippets와 index HTML 생성 확인. frontend 전체2037개 통과. 리뷰 finding0.
