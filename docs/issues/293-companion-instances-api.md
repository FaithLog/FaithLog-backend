# 개체별 동물 저장·돌봄·진화 API (#293)

## DB 구조 및 무손실 이전
- `V33__add_companion_instances_and_care.sql`은 기존 `user_companions.quantity`마다 별개의 UUID 동물을 생성한다.
- 이전 동물의 상태·성장 정보를 임의로 소급하지 않는다. 기존 수량은 보존하고, 감소 시간은 기능 적용 시각부터 시작.
- **기존 클라이언트의 뽑기/판매 API는 그대로 유지**한다. `quantity` 변경 트리거가 개체 수를 함께 조정하므로 중복을 잃지 않는다.
- 판매 시 성장·돌봄 이력이 있는 동물을 임의 삭제하지 않는다. 현재 `garden_placements`는 디자인 단위라 배치된 디자인의 첫 개체를 보호한다.
- 따라서 **판매 가능한 방치하지 않은 Lv1 개체가 부족하면 기존 수량상 판매 가능해 보이더라도 판매가 실패**할 수 있다. 출시 전 인스턴스 기반 판매/배치 UI와 오류 표시 개선이 필요하다.

## 구현된 엔드포인트
- `GET /api/v1/companions/me/instances` : 개체별 UUID, 디자인, Lv, 현재/누적 경험치, 돌봄 일수, 상태 5개 게이지, 마지막 돌봄 시각, 버전 및 진화 조건
- `POST /api/v1/companions/me/instances/{id}/care`: 헤더 `Idempotency-Key`, JSON `{"action":"FEED","expectedVersion":0}`
- `POST /api/v1/companions/me/instances/{id}/evolve`: 헤더 `Idempotency-Key`, JSON `{"expectedVersion":1}`
- `GET`은 서버 시각으로 상태 감소를 계산해 응답하되 매번 DB를 갱신하지 않는다. 돌봄/진화 POST에서는 이전 감소분을 정산한 후 기록한다.
- POST는 사용자 인증/소유권, 잠금/버전, 동일 요청 키의 멱등 응답, 행동별 쿨타임 및 일일 XP 100 제한 검사를 수행한다.
- 하루는 서버 `Asia/Seoul` 기준. 기절 상태(HP 0)도 먹이·청소·수면으로 회복 가능. 영구 사망 없음.
- 임시 회복 수치: 먹이(+35 배부름/+3 건강), 쓰다듬기(+22 친밀/+12 행복), 놀이(+30 행복/+7 친밀/-3 배부름), 청소(+45 청결/+5 건강), 수면(+15 행복/+15 건강).
- 수면 연속 누르기로 무제한 건강 회복하는 문제를 막기 위해 4시간 쿨다운(성장 XP 0).
- 예수님 미라클은 해당 진화 외형이 없으므로 진화 불가. 기존 코스튬 ID 및 뽑기 구조 변경 없음.

## 위험/후속 작업
- **현재 정원 배치 정보는 디자인 ID로만 저장**한다. 동일 디자인 중 어느 개체를 움직이는지 아직 구분하지 않으므로, 선택한 동물의 `instanceId`를 `garden_placements`에 매핑하는 후속 마이그레이션과 API를 해야 한다.
- 현재 프론트 홈에서는 새 API나 `FarmHudGauge`를 호출하지 않는다. frontend #348에서 터치 시 선택 동물의 상태·경험치와 돌봄 버튼을 연결하고, 최신 버전 충돌 UI를 제공해야 한다.
- 기존 판매 API는 디자인별 수량만 받으므로, 성장 개체를 판매하려면 별도의 개체 선택 UI/보호 정책이 필요하다.
- 모바일 단말 실기기/성능/시각 QA 및 메인 배포는 완료되지 않았다.

## 홈 조회 최적화 (#295)
- `GET /api/v1/companions/me/instances/garden`는 현재 배치된 최대 5개만 `garden_placements` + 인덱스 LATERAL JOIN으로 조회한다.
- `GET /api/v1/companions/me/instances?page=0&size=50`는 소유 개체 목록 페이지네이션. 최대 100개/요청, page 0~100, 한 요청으로 수천 개를 조회하지 않도록 제한한다.
- GET는 사용자 `is_active/deleted_at`만 확인하고 **계정/개체에 FOR UPDATE 잠금을 걸지 않는다**. 읽기마다 상태를 저장하지 않으며, 돌봄·진화 POST에만 트랜잭션 잠금을 적용한다.
- 홈 쿼리의 효율은 `ix_companion_instances_user_design`, 목록 정렬은 `ix_companion_instances_owner_acquired` 인덱스로 뒷받침한다.
- 기존 디자인별 배치는 첫 번째 인스턴스를 선택한다. 동일 디자인 두 마리의 서로 다른 돌봄·배치는 후속 인스턴스 배치 개발에 속한다.
- 실제 응답 시간/쿼리 실행 계획은 운영 대표 데이터로 별도 측정해야 한다. 검증 전 특정 ms/배수 향상 주장은 하지 않는다.
