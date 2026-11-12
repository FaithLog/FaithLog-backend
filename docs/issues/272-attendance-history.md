# #272 출석 지급·뽑기 이력

기존 승인된 출석 10P를 계정 공통 지갑에 연결한다. 서버 Clock/Asia-Seoul 날짜로 `attendance:YYYY-MM-DD` 원장 키를 만들고 계정 잠금과 기존 원장 고유 제약으로 중복·동시 요청을 직렬화한다. 별도 마이그레이션이나 운영 포인트 보정은 없다.

- GET /api/v1/points/me/attendance: 오늘 날짜, 10P, 수령 여부, 현재 잔액.
- POST /api/v1/points/me/attendance: 오늘 출석 보상 수령. 날짜·금액 입력 없음. 재요청은 추가 지급 없이 현재 상태 반환.
- GET /api/v1/companions/me/draws?page=0: 본인의 DRAW 결과만 최신순 20건. page 범위 0~10000. 저장 당시 결과 및 시각을 반환하며 결과 balance는 당시 잔액이다.

테스트: 한국 시간 자정 경계 단위 테스트 2개 로컬 통과. PostgreSQL QA에 동시 출석 1회 지급, 다른 계정 분리, rollback, 이력 페이지/재요청 중복 없음, HTTP 인증·출석 조회·수령·이력 REST Docs 추가. 전체 CI 결과는 실행 후 기록한다. 로컬 Gradle wrapper는 배포본 다운로드 네트워크 오류로 실행되지 않아 Java21 직접 컴파일/JUnit으로 단위 검증했다.

남은 미션은 docs/decision-log.md의 주간·월간 별도 확정 조건에 따라 지급 구현을 보류한다. 월 경계 주차, 미제출자의 무벌금 인정, 신규 가입자의 직전 기간 인정 기준은 기존 데이터에 명확한 보상 정책이 없다. 프론트 실 API 연결·공개 및 운영 배포는 별도 단계다.

Obsidian 지정 경로와 Projects 권한을 이 환경에서 확인할 수 없어 저장소 문서를 대체 기록으로 유지한다.
