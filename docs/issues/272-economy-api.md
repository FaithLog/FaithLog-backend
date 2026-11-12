# #272 뽑기·판매 API 초안

사용자는 기존5등급으로 이어서 작업하도록 승인했다. standard-v1은 일반54/고급30/희귀11/에픽4/유니크1%, 1회100P/10회1000P. 판매가는10/15/25/40/50P. V29 Supabase 적용 및 Flyway 이력 등록. 기존 사용자 포인트·보유 동물 지급/차감은 수행하지 않았다.

작성 API:
- GET /api/v1/companions/draw-pools: 활성 poolVersion, unitCost,totalWeight, 디자인별 weight (확률=weight/totalWeight).
- POST /api/v1/companions/me/draws: Idempotency-Key 헤더, {poolVersion:"standard-v1",count:1 또는10}
- POST /api/v1/companions/me/sales: Idempotency-Key 헤더, {designId:"...",quantity:1}
응답 operationId,designIds,pointDelta,balance,policyVersion,quantity,unitPrice. balance는 해당 처리 완료 시점 snapshot. 재요청은 과거 결과를 그대로 반환하므로 최신 지갑 조회와 구분.

계정 row lock → 동일 kind/key 요청 replay 검사 → 서버 정책 검증 → 지갑 차감/적립 및 보유 갱신 → 응답 snapshot 저장. 동일 키·다른 요청409. point transaction과 operation은 business_key 복합 FK. 예외 시 모두 rollback. 뽑기는 SecureRandom과 정책 weight로 서버가 결정하며 클라이언트 결과/단가를 받지 않는다. 판매 후 최소1마리 유지. API는 DTO만 반환.

poolVersion마다 entry를 별도 관리하므로 시즌을 추가해도 기존 결과 snapshot은 유지된다. 공개한 정책은 UPDATE 대신 새 버전을 등록하는 운영 규칙이며 DB 불변 trigger는 아직 미구현. 레전드 등급 확장은 이번 범위 밖이다.

검증: Supabase에서 실제 희귀도별 가중치 합계/백분율, 판매가, RLS/anon 차단 및 operation0건 확인. 잘못된 요청 방어 테스트1개 추가 작성. JDK/Gradle 부재로 Java compile/JUnit/HTTP 및 동시성·재요청 통합 검증 미실행. 전 단계 초안과 함께 배포·병합 전 반드시 CI/REST Docs/통합 테스트를 수행해야 한다. 실제 운영 API는 미배포. 뽑기 이력 목록·미션 나머지·프론트 실API 연결은 남아 있다.
