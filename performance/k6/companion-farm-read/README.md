# #297 FaithLog 동물농장 읽기 전용 성능 기준선

상태: **scenario-ready / not measured**. API, DB, Flyway, 배포 설정, 운영 데이터는 변경하지 않는다.

## 실행 경계

- 개발자가 직접 격리한 **로컬 Docker/스테이징 복제본**에서만 실행한다. URL은 localhost, 127.0.0.1, host.docker.internal 또는 로컬 Compose 서비스 app만 허용한다. **운영/Cloud Run 원격 부하 불가**.
- 50 / 200 / 300명의 별도 합성 사용자 데이터와 사용자당 1~200개의 동물, 정원 0~5개 배치를 사전에 준비한다. 이 스크립트는 fixture를 생성하지 않고, 세 규모의 실제 데이터를 보증하지 않는다.
- 로그인은 setup에서 VU당 한 번 실행한다(테스트 사용자의 Redis 세션/로그인 시각에 영향 가능). 측정 iteration은 GET만 사용하며 POST 돌봄/진화/뽑기/판매/미션 수령을 수행하지 않는다.
- 사용자 이메일·비밀번호·토큰은 **커밋/PR/로그/보고서로 보내지 않는다**. 기존 성능 harness와 동일하게 사전에 격리 계정의 유효 기간과 측정 시간 창을 확인한다.
- 정원/인스턴스 5마리 상한과 API 성공 shape를 검사하고, 엔드포인트별 p50/p95/p99 및 요청 실패율을 기록한다. 지금은 지연 목표나 성능 개선 수치를 확정하지 않는다.

## 사용자 파일

.gitignore에 포함되는 build/ 아래에 예를 들어 build/perf-farm-users.json 파일을 생성한다.

~~~json
[
  { "email": "perf-farm-001@example.invalid", "password": "local-test-only", "campusId": 1 }
]
~~~

위 값은 **예시이며 로그인되는 실제 계정이 아니다.** 실제 테스트 데이터의 각 email은 반드시 perf-로 시작해야 한다. VUS보다 계정 수가 같거나 많아야 하고 상한은 300이다. INCLUDE_MISSIONS=true일 때 각 계정의 승인된 테스트 캠퍼스 ID가 필요하다.

## 로컬 스모크

~~~bash
mkdir -p build/reports/k6/issue-297
BASE_URL=http://127.0.0.1:8080 \
ISOLATED_FIXTURE_ACK=yes \
PERF_USERS_FILE=build/perf-farm-users.json \
VUS=1 DURATION=30s \
k6 run --summary-export build/reports/k6/issue-297/smoke.json \
  performance/k6/companion-farm-read/baseline.js
~~~

스모크 성공 후 별도로 격리된 50 / 200 / 300명 데이터셋 각각에 맞춰 VUS=5,10,30 등 **점진적**으로 올린다. 숫자는 권장 시작 예시이지 승인된 목표나 서버 용량이 아니다. 한 번에 하나의 데이터셋과 부하만 실행한다. 모든 시험 결과에 dataset ID, 인원/동물/배치 수, 실행 Git SHA, PostgreSQL 버전, VUS, duration, API 성공률, p50/p95/p99, 요청량, 호스트 CPU/메모리/DB 풀과 다른 트래픽 여부를 기록한다.

주간/월간 미션 동시 조회를 포함하려면 INCLUDE_MISSIONS=true를 추가한다. 클라이언트 앱 복귀 30초 내 전체 API 재조회 억제는 이 서버 스크립트로 검증하지 않으며 **프론트 회귀 테스트/실기기 네트워크 로그**가 필요하다.

## 별도 보류 범위

- POST /care·/evolve의 동시성, 멱등성·잠금 경합/재시도: 분리된 test fixture와 엄격한 사후 XP/버전/로그 검증 후 별도 측정
- 1회/10회 뽑기·판매의 업데이트/트리거 비용: 독립 쓰기 시나리오와 롤백/정리 방안 후 측정
- EXPLAIN(ANALYZE, BUFFERS): 전용 DB에서 garden_placements(user_id,slot), companion_instances(user_id,id), (user_id,design_id,acquired_at,id), (user_id,acquired_at,id) 인덱스 후보, seq scan/행수/버퍼 확인. Planner 통계와 데이터 cardinality 없이는 강제 index-scan 주장 금지
- XP/방치 1·30·365·100000일 CPU 및 정확도: 기존 CompanionExperienceDecayPolicyTest와 CompanionNeglectPolicyTest의 기능 계약을 확인하고 별도 microbenchmark 설계
- 실제 p95 목표·합격 기준과 운영 배포 결정: 사용자 승인 및 iOS/Android QA 이후

## 안전성·보고

로그·k6 summary에는 응답 본문/인증 토큰을 기록하지 않는다. 로컬 테스트 종료 시 부하 프로세스·계정·DB 원상 복구를 확인하고 raw 결과물은 build/reports 아래에만 보관한다. 측정이 **실제로 실행되기 전**에는 퍼센트 개선이나 최대 동시 접속 인원을 주장하지 않는다.
