# 동물농장 성장·진화 정책 / 단계별 구현 계획 (#287)

## 현재 상태

- 이번 PR은 **순수 자바 정책 + 단위 테스트 + API/데이터 설계 문서**만 추가하며 운영되는 진화/돌봄 API를 변경하지 않는다.
- 원래 13종 × 10디자인을 뽑는 방식과 개별 코스튬 판매 금지 원칙을 유지한다.
- 프론트에는 Lv2/Lv3 에셋·돌봄 HUD가 있지만 실제 홈은 기존 `BibleFriendDesignSprite`로 그린다.
- 현재 재고는 `user_companions(user_id,design_id,quantity)`로 묶어 관리한다. 중복 동물마다 성장상태를 따로 저장할 수 없다. 개체 ID를 먼저 도입해야 한다.

## 임시 성장 정책 (실제 운영 숫자 확정 전)

| 다음 단계 | 현재 성장 XP | 돌봄한 서로 다른 날짜(KST) | 건강 | 배부름 | 청결 |
|---|---:|---:|---:|---:|---:|
| Lv1 → Lv2 | 300 | 3 | 60 | 50 | 50 |
| Lv2 → Lv3 | 1,000 | 10 | 70 | 60 | 60 |

- `growthXp`는 **현재 성장 XP**로서 방치하면 감소할 수 있다(#291). `totalEarnedXp`는 누적 획득량이며 감소하지 않는다. 하루 XP 획득 최대 100. Lv1/2/3의 XP 하한은 각각 0/300/1000.
- 행동별 임시 쿨다운/획득 XP: 먹이 4시간/15XP, 쓰다듬기 1시간/10XP, 놀기 2시간/20XP, 청소 3시간/15XP. 수면은 0XP.
- 건강·배부름·청결도는 0~100. 낮을 때 진화만 제한. 건강이 0이어도 영구 삭제하지 않고 기절/회복 상태로 관리할 것을 권장한다.
- 사용자가 직접 진화 버튼을 눌러야 레벨이 올라간다. **진화하면 그 단계의 XP 하한이 영구 보호**되며 레벨은 하락하지 않는다. 진화 시 XP·상태는 유지하고 뽑기 포인트를 차감하지 않는다.
- 예수님 미라클은 Lv2/Lv3 에셋이 없으므로 당장은 진화 대상에서 제외.
- 소셜/신앙 미션 포인트는 별도 화폐이고 돌봄 XP로 바로 환산하지 않는다.

## 1. 데이터 이전 계획

1. `companion_instances(id UUID PRIMARY KEY,user_id,design_id,level, growth_xp, total_earned_xp, care_days,health,satiety,cleanliness,joy,affection,last_evaluated_at,last_cared_at,elapsed_hours,updated_at,created_at,version)` 추가. `UNIQUE(user_id,id)` 및 유저/디자인 인덱스.
2. 기존 `user_companions.quantity`만큼 인스턴스 레코드로 **중복을 보존해 백필**. 기존 `acquired_at` 보존, 임의로 돌봄 횟수/진화 진행도를 부여하지 않는다.
3. 점진적 변경 동안 기존 재고 집계는 이전 버전 클라이언트를 위해 유지한다. 뽑기/판매 트랜잭션은 인스턴스 생성/삭제와 재고 집계를 원자적으로 동기화하고 일치성 검증 테스트를 작성.
4. 현재 판매 API는 `designId + quantity`로 동작함. 진화 개체를 임의 판매하지 않도록 해당 디자인의 **기본 Lv1 미배치 개체 우선** 판매하고, 개체 선택 UI를 추가하기 전에는 성장 개체 판매를 막는 정책을 권장.
5. 정원 배치는 현재 `designId`로만 저장됨. 같은 디자인의 서로 다른 개체 배치까지 허용하려면 `companionInstanceId`로 키를 변경하고 기존 배치를 한 개의 인스턴스에 대응시키는 이전 작업이 필요.

## 2. 확정해야 할 API 계약

- `GET /api/v1/companions/me/instances`: 개별 소유 동물의 `instanceId`, `designId`, `level`, `growthXp`, `totalEarnedXp`, `stats`, `careDays`, `version`, `nextEvolution` 노출.
- `POST /api/v1/companions/me/instances/{id}/care`: `{action, requestId}`; 서버 시각으로 쿨다운·일일 XP·상태 변화 계산, 성공 결과와 새 버전 반환.
- `POST /api/v1/companions/me/instances/{id}/evolve`: `{requestId, expectedVersion}`; 행 잠금 → 상태 재평가 → 진화 → 상태 저장 → 결과 반환.
- `companion_care_events`: `(user_id,instance_id,request_id)` UNIQUE, 행위 종류, XP 변화, 이전/이후 게이지, 서버 시각. **중복 요청은 이전 결과를 재생**.
- 다중 단말/연속 탭이 동시 XP를 획득하지 못하도록 인스턴스에 대한 트랜잭션 및 버전 검사.
- 배부름/청결 감소는 마지막 관리 시각에서 **조회/행동 시 지연 계산**해 DB 작업을 줄이고 매분 Cron/요청을 피한다. KST 날짜 계산은 서버 `Asia/Seoul`로 고정.

## 3. 프론트 연동

- 정원에 배치된 동물 5마리는 그대로 자유롭게 움직인다. **선택된 한 마리**를 터치했을 때만 하단 패널에 `FarmHudGauge`와 돌봄 5종/진화 버튼 표시.
- 단계·디자인에 따라 `BibleFriendFarmSprite` 호출. 코스튬의 `designId`는 진화 후에도 바꾸지 않는다.
- 쿨다운 남은 시간, 회복이 필요한 상태, 진화 가능 조건을 서버 응답으로 표시. 서버 미지원 상태라면 버튼은 비활성화/안내하며 **가짜 게이지를 보여주지 않는다**.
- 에셋은 `assets/companions/farm-hud`에서 오프라인 번들. 기존 `theme.ts`의 블루/청록 색상·여백·Pretendard와 하단바 유지.

## 4. 배포 게이트

정책 클래스는 아직 어떠한 컨트롤러에서도 사용하지 않으므로 이 PR만 배포해도 돌봄 기능은 활성화되지 않는다. 마이그레이션, API, 프론트 상태 연결, 동시성/멱등성 및 기존 뽑기/판매 QA, 실기기 애니메이션/가독성 검증 이후에만 main/스토어 배포.

