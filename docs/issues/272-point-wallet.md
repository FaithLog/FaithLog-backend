# #272 단계 1: 계정 공통 포인트 지갑 초안

사용자 승인: 계정 공통 소유 설계로 단계별 개발 진행. 초기 운영 지급은 0P, 체험 1200P는 이관하지 않는다.

구현 초안: V25 wallet/transaction, 계정 row 잠금에 의한 최초 생성 직렬화, business key UNIQUE 및 동일 금액 재요청 no-op, 음수 잔액/overflow 검증, 인증 사용자 본인 잔액 및 20개 단위 거래 내역 조회. 거래는 내부 settle만 제공하며 외부 임의 포인트 변경 API는 없다.

API:
- GET /api/v1/points/me/wallet → balance,version
- GET /api/v1/points/me/transactions?page=0 → delta,balanceAfter,createdAt 목록 (page 0..10000)

검증 상태: 도메인3/서비스2 테스트 작성. RED 실행 시 Gradle 배포본이 없고 services.gradle.org 네트워크가 차단되어 compile/test 전에 실패. JRE21만 존재하고 javac/jar도 없어 독립 컴파일 불가. 따라서 테스트 통과/RED 재현을 주장하지 않으며 리뷰 전 초안으로 유지. 운영 DB 미적용, push/PR/merge 없음.

남은 단계1 완료 조건: DTO/port 레이어 및 REST Docs를 기존 아키텍처와 맞춰 정리, PostgreSQL 마이그레이션 및 동시성/롤백 검증, API 인증/비활성 테스트, 전체 test/build/asciidoctor, 코드 리뷰.

단계2 보류: 일간 보상 지급 연결 및 과거 수령 이관. 현재 기존 API와 원장 코드는 연결되지 않았다. 신규 포인트 조회는 기존 누적 수령을 자동 포함하지 않는다. 기초 지갑 검증 완료 후 연결할 것. 기존 캠퍼스 lock과 신규 계정 lock의 순서/회원 탈퇴 잠금 경합 검토도 포함.

GitHub Projects/Obsidian 연결은 현재 작업에서 수행하지 않음. #272는 진행 중이며 완료 처리하지 않는다.

## Supabase 적용 및 일간 보상 코드 연결 (2026-11-11)
사용자가 Supabase 적재 및 코드 작성을 명시 승인했다. FaithLog 프로젝트 zgekxcqohujikxbibaac에서 V23까지 적용된 상태를 확인하고 V24/V25 SQL과 Flyway checksum 이력을 같은 migration 작업에서 적용했다. V26은 기존 V24의 RLS 및 권한 회수를 재현 가능하게 추가했다. 보상·지갑 기존 row는0건으로 데이터 이관 없이 진행했다.
3개 테이블 RLS=true, anon SELECT=false, authenticated INSERT=false 확인. 운영 잔액/샘플 포인트를 생성하지 않았다.
CalendarDailyRewardService는 계정 row → campus member row 순서로 잠그고, 수령과 pointWallet.settle을 같은 transaction으로 연결한다. business key는 daily:서버날짜:mission으로 campus를 제외해 중복 적립을 차단한다. 기존 totalAwardedPoints 의미는 유지한다.
주의: 백엔드 코드는 아직 배포되지 않았다. JDK/Gradle 부재로 컴파일·Spring 통합 테스트 미실행 상태다. DB 반영 성공과 앱 API 실행 검증은 다르며, API 배포 전에 CI/동시성·rollback/REST Docs 검증이 남아 있다.
