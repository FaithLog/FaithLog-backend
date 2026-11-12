# #272 단계4 정원 저장

작성 API (미배포): GET/PUT /api/v1/companions/me/garden.
PUT 예: {"version":0,"backgroundId":null,"placements":[{"slot":1,"designId":"david-lamb:base","x":0.5,"y":0.5}]}.
응답: version,backgroundId,placements. 계정 공통 소유. 최초 상태는 배치0/배경null(기본 표시)이며 소유 자산 자동 지급 없음. null 배경은 구매 배경을 장착하지 않은 상태다.

- 계정 row 잠금으로 첫 생성 및 갱신 직렬화. version 불일치409 COMPANION_GARDEN_CONFLICT. 미소유403 COMPANION_NOT_OWNED.
- 슬롯1..5, 최대5종, 슬롯·디자인 중복 금지, 좌표 finite 및0..1. 보유 배경·동물 복합 FK 및 앱 검증.
- 교체/해제/좌표/배경은 전체 상태 PUT 한 transaction. DELETE/INSERT 또는 갱신 실패 시 전체 rollback.
- inventory filter UNEQUIPPED 추가, 기존 정렬보다 배치 우선 그룹 적용.
- 배경 초기 무료 지급 및 뽑기/판매 정책을 임의로 추가하지 않음.

Supabase V28 적용 및 Flyway 이력 기록. user_gardens/garden_placements RLS=true, anon SELECT=false/authenticated INSERT=false 확인. rollback transaction에서 slot6/NaN 입력이 CHECK 위반으로 거부됨을 검증. 시험 후 정원/배치 row0건 확인.

Java 입력 테스트3개 작성. JRE만 있고 javac/Gradle 실행 배포본이 없어 컴파일·JUnit·HTTP/동시성 테스트는 여전히 미실행이다. 앞선 지갑·카탈로그도 미검증 초안이며 배포/머지하지 않았다. 다음 기능 추가 전 전체 CI 및 REST Docs/서비스 레이어 검증 필요.
