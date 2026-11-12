# #272 develop 연동 중 판매 정책 조회 보완
프론트가 판매 확인 화면에서 서버 단가를 표시할 수 있도록 GET /api/v1/companions/sale-prices를 제공한다. 기존 판매 정책 테이블을 읽으며 DB·가격·정산 정책은 변경하지 않는다. 활성 사용자 인증을 검증하고 등급별 unitPrice/policyVersion을 반환한다. 기존 PostgreSQL HTTP QA에 미인증 차단 및 실제5등급/COMMON10P 응답·REST Docs 검증을 추가했다.

사용자가 기존 PR들의 develop 병합 및 develop 기준 프론트/백엔드 연결·QA를 지시했다. 백엔드273과 프론트311은 develop에 병합했다. 연동 수정도 검증 후 develop에 반영한다. main/스토어 배포는 범위 밖이다.
