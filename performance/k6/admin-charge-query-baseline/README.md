# Issue #193 Admin Charge Query Baseline

Status: **scenario-contract-ready, runner/evidence pending, not measured**.

이 디렉터리는 Issue #193의 current-develop 호환 correctness와 before 측정 계약만 준비한다. 현재 wave에서는 #192가 measurement slot을 먼저 사용하므로 fixture SQL, DB 조회, preflight HTTP, k6를 실행하지 않는다.

## Immutable baseline server

- source commit: `355f79df5b2e47636b7d1a17dea029da6c93c62d`
- base URL: `http://127.0.0.1:28080`
- app container: `901dbab3949fc669e7902e6c1471f4d60ffc80b049efa0f9a5203343710a7868`
- image: `sha256:759dbf31b1a3ae2261ccc6e409af3a1c82f64c487b53bfe7f1af74d5bd2f4d07`
- Compose: `faithlog-frontend-latest/app`
- migration: Flyway V11

실행 시에는 위 source/API/container/image/Compose/Flyway/health identity가 모두 같아야 한다. 다른 성능 측정, frontend QA, fixture write, DB 조사와 동시에 실행하지 않고 공통 performance lock을 사용한다. Docker build, restart, prune은 금지한다.

## Measured 16-case contract

대상 endpoint는 다음 두 개다.

- `GET /api/v1/admin/campuses/{campusId}/charges`
- `GET /api/v1/admin/campuses/{campusId}/charges/my-accounts`

16개 measured case는 프론트 요청과 같은 `size=10`을 항상 명시한다. backend controller의 default 20은 이 성능 시나리오 입력이나 새 제품 계약으로 사용하지 않는다. 모든 measured query는 `page`, `size=10`, `sort=createdAt,desc`, `includeArchived=false`를 명시한다.

응답은 `summary`, 정렬된 `members[]`, 실제 DTO metadata인 `page`, `size`, `totalElements`, `totalPages`를 SQL expectation과 exact 비교한다. DTO에 없는 `number`, `first`, `last`는 기대하거나 추가하지 않는다.

측정 순서는 다음과 같다.

1. my-accounts `PENALTY + UNPAID`
2. my-accounts category
3. my-accounts status
4. my-accounts userId
5. my-accounts keyword
6. my-accounts의 현재 unknown `paymentAccountId` 무시 동작
7. my-accounts page 0
8. my-accounts page 1
9. admin `PENALTY + UNPAID`
10. admin category
11. admin status
12. admin userId
13. admin keyword
14. admin paymentAccountId
15. admin page 0
16. admin page 1

## Separate correctness gates

16개 latency Trend와 별도로 다음을 preflight에서 검증한다.

- `includeArchived=false`: 생성 시각이 오래되어도 `UNPAID`는 포함한다.
- terminal 1개월 cutoff: `PAID`는 `paidAt`, `WAIVED`/`CANCELED`는 `updatedAt` 기준 최근 1개월만 포함한다.
- `includeArchived=true`: 1개월 이전 terminal row를 포함한다.
- #200 COFFEE duty: 일반 ACTIVE COFFEE 담당자는 filter 생략 시 본인의 ACTIVE·미삭제 계좌 전체 aggregate만 조회하고, 본인 계좌 filter는 성공하며, 다른 담당 계좌 filter는 403이다. 회원별 COFFEE 상세는 production의 historical read 계약과 동일하게 본인 소유 COFFEE 계좌 전체 item만 반환한다.
- measured credential인 service ADMIN의 전체 read, cross-campus account 404, source unique duplicate 0을 검증한다. campus manager full-read는 current develop의 별도 production test를 정적으로 대조했으며, runtime manager credential을 승인받기 전에는 이 preflight의 실측 검증으로 주장하지 않는다.

fixture는 승인된 service ADMIN과 일반 duty user를 포함해 기존 ACTIVE user row 정확히 1,000개만 선택하고, 각 execution의 exact `datasetId`로 새 campus를 create-only 생성한다. 그 campus에는 ACTIVE membership 1,000개, 계좌 5개, charge 35,000개만 만든다. 다른 `datasetId`의 before marker가 남아 있어도 새 namespace는 허용하며, 현재 `datasetId + fixtureRunId`의 중복만 INSERT 전에 거부한다. before와 integration after는 서로 다른 ID를 쓰되 위 shape는 같다. cleanup, DB restore, snapshot restore를 구현하거나 전제하지 않는다. SQL session timezone은 앱의 달력 기준과 같은 `Asia/Seoul`로 고정한다.

## Measurement approval gate

사용자 승인 전 before 측정과 production 최적화는 금지한다. #192 종료와 measurement slot 인계, runtime admin/duty credential, 1,000 ACTIVE user pool, workload 값 승인을 모두 받은 뒤에만 runner/evidence를 별도 리뷰하고 실제 수집을 진행한다.

PM 승인 요청용 추천값은 다음과 같다.

- `WARMUP_ITERATIONS=5`: 한 iteration이 16 endpoint call이므로 총 80 call로 JVM/DB cache를 예열한다.
- `WARMUP_VUS=1`: frontend 순서를 보존하고 measured concurrency와 warmup을 분리한다.
- `WARMUP_MAX_DURATION=5m`: N+1 before가 느려도 정상 warmup을 임의 중단하지 않는 상한이다.
- `MEASURED_VUS=10`, `MEASURED_DURATION=3m`: read-only aggregate의 p95/p99 표본을 확보하면서 shared local PostgreSQL에 VUS 30부터 바로 가하는 위험을 피한다.
- `TOKEN_EXPIRY_SAFETY_SECONDS=120`: 각 phase 종료와 evidence 수집 여유를 확보하되 Access Token 정책을 바꾸지 않는다.
- `DOCKER_STATS_SAMPLING_INTERVAL_SECONDS=1`: 3분 구간의 CPU/RAM peak를 놓치지 않고 #192 resource sampling과 같은 해상도를 사용한다.

이 값은 추천일 뿐 승인값이 아니다. 승인 전에는 default로 실행되지 않는다. after 측정과 개별 branch Docker build는 PM integration branch까지 금지한다.
