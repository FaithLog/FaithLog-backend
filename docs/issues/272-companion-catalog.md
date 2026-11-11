# #272 단계 3 — 동물·배경 카탈로그 및 보유 조회

V27 Supabase 반영: companion_designs, user_companions, backgrounds, user_backgrounds. 프론트 ID/이름/희귀도와 동일한 13종×10의상=130 디자인 및5 배경 seed. 사용자 보유 동물·배경 자동 지급은 하지 않았다. asset_key는 앱 내부 에셋 조회 키이며 CDN URL/이미지 업로드를 의미하지 않는다. 확률 및 정식 판매가는 이번 단계에 포함하지 않는다.

작성 API (미배포):
- GET /api/v1/companions/catalog: 활성 카탈로그
- GET /api/v1/companions/me/inventory?filter=OWNED&rarity=UNIQUE&query=사자&sort=RARITY&page=0&size=20
- GET /api/v1/companions/me/backgrounds: 배경 및 본인 소유 여부
filter: OWNED(default), DUPLICATES, ALL. sort: CATALOG(default), RARITY, COUNT. size 1..100, page 0..10000. 이름/동물 종류 검색100자, SQL wildcard literal escape. 바인딩 쿼리 사용. 정렬은 allowlist 고정 SQL. 보유한 비활성 디자인은 보유목록에서 계속 조회 가능. DTO만 반환하며 다른 사용자 ID를 입력받지 않는다. 정원 테이블이 없는 단계이므로 미배치 필터·배치 우선 정렬은 후속 단계에서 추가.

검증: Supabase seed 개수, 희귀도 분포, 검색 SQL, RLS/권한 확인. Java 입력 검증3/인가1 테스트 작성. 환경의 JDK/Gradle 부재로 실행하지 못함. 코드 컴파일·통합/API 테스트·REST Docs는 미완료다. 기존 지갑/일간 보상 코드도 동일하게 검증 전이므로 단계1~3 전체 완료로 간주하지 않는다.

다음: CI 실행 환경 확보 후 지갑/일간 보상/카탈로그 회귀 검증과 REST Docs 완료, 이후 정원 저장 구현. 운영 API 배포·main/develop merge 없음.
