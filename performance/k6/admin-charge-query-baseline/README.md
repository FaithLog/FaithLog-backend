# Issue #193 Admin Charge Query Baseline

Status: **scenario and runner/evidence contract-ready, fake/static verified, not measured**.

이 디렉터리는 Issue #193의 current-develop 호환 correctness와 before 측정 계약을 준비한다. B/C/D actual attempt는 각각 preflight에서 거부됐고 k6 warmup/measured는 모두 0건이므로 유효 baseline은 아직 없다.

최신 #206 서버 기준 runner는 Node fake contract, JS/MJS·shell 구문, SQL 정적 mutation 차단까지만 다시 검증한다. PM의 독립 리뷰 전에는 fresh fixture, preflight HTTP 또는 k6를 실행하지 않는다.

## Immutable baseline server

- source commit: `6796ed146244d8f3f5b5dd7048ebe16865084a97`
- base URL: `http://127.0.0.1:28080`
- app container: `a7df78b330f457a7fd60a9531362d0f1f063ae7aa6cae5f2d996eb8cb51fe79d`
- app image: `sha256:8e0f8d85d697a7d34aabf3703ddb27b4f1af326dec4f7c35556986303b0b816c`
- app StartedAt: `2026-07-16T04:23:10.082407837Z`
- Compose: `faithlog-frontend-latest/app`
- app working directory: `/private/tmp/FaithLog-perf-206-deploy`
- PostgreSQL container: `81aa74ca1b491b45eb691b3d65de9e42eb47ef64a6bcb961d0b627b030139ae9`
- PostgreSQL image: `sha256:48d29282d2b43c402465c28f8572021b59aaf43574056faaad2fd7bb85ffdd4e`
- Redis container: `4109f6525948d12d1e5377fb6160c8955f6c3fcd7816e02786b2dd8031e23de9`
- Redis image: `sha256:80dd823f4d2bf93dd5e418a0ae2817319a1ba279953e234082e54a5a18306223`
- migration: Flyway V11
- health: `UP`
- scheduler: `false`

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
- #206 stable charge-item ordering: 모든 charge-item pageable endpoint는 사용자가 지정한 primary sort와 같은 방향의 `id` secondary sort를 자동 적용한다. 이 scenario의 member-detail은 `createdAt,desc` 동률에서 `id,desc`를 exact 검증하며 validator를 완화하거나 fixture timestamp를 인위적으로 벌리지 않는다.
- measured credential인 service ADMIN의 전체 read, cross-campus account 404, source unique duplicate 0을 검증한다. campus manager full-read는 current develop의 별도 production test를 정적으로 대조했으며, runtime manager credential을 승인받기 전에는 이 preflight의 실측 검증으로 주장하지 않는다.

fixture는 승인된 service ADMIN과 일반 duty user를 포함해 기존 ACTIVE user row 정확히 1,000개만 선택하고, 각 execution의 exact `datasetId`로 새 campus를 create-only 생성한다. 그 campus에는 ACTIVE membership 1,000개, 계좌 5개, charge 35,000개만 만든다. 다른 `datasetId`의 before marker가 남아 있어도 새 namespace는 허용하며, 현재 `datasetId + fixtureRunId`의 중복만 INSERT 전에 거부한다. before와 integration after는 서로 다른 ID를 쓰되 위 shape는 같다. cleanup, DB restore, snapshot restore를 구현하거나 전제하지 않는다. SQL session timezone은 앱의 달력 기준과 같은 `Asia/Seoul`로 고정한다.

## Measurement approval gate

사용자 승인 없는 before 측정과 production 최적화는 금지한다. 승인된 G는 resource cadence validation에서 rejected됐으며, fresh H는 PM의 새 독립 measurement-ready 리뷰와 사용자 승인 전에는 실행하지 않는다. 승인 후에도 한 서버 한 load 원칙, runtime admin/duty credential, 1,000 ACTIVE user pool, 승인 workload와 fresh namespace를 모두 충족한 PM 실행에서만 실제 수집을 진행한다.

fresh H 제안 식별자는 `I193_BEFORE_20260716_H / I193_FIXTURE_20260716_H / EXEC193_BEFORE_20260716_H`다. 아직 생성하거나 실행하지 않았으며 B/C/D/E/F/G namespace와 report는 절대 재사용하지 않는다.

PM 승인 요청용 추천값은 다음과 같다.

- `WARMUP_ITERATIONS=5`: 한 iteration이 16 endpoint call이므로 총 80 call로 JVM/DB cache를 예열한다.
- `WARMUP_VUS=1`: frontend 순서를 보존하고 measured concurrency와 warmup을 분리한다.
- `WARMUP_MAX_DURATION=5m`: N+1 before가 느려도 정상 warmup을 임의 중단하지 않는 상한이다.
- `MEASURED_VUS=10`, `MEASURED_DURATION=3m`: read-only aggregate의 p95/p99 표본을 확보하면서 shared local PostgreSQL에 VUS 30부터 바로 가하는 위험을 피한다.
- `TOKEN_EXPIRY_SAFETY_SECONDS=120`: 각 phase 종료와 evidence 수집 여유를 확보하되 Access Token 정책을 바꾸지 않는다.
- `DOCKER_STATS_SAMPLING_INTERVAL_SECONDS=1`: blocking capture를 back-to-back으로 요청하는 nominal cadence metadata다.
- `DOCKER_STATS_MAX_GAP_SECONDS=5`: Docker Desktop `--no-stream` capture overhead를 포함한 인접 sample 간 승인 maximum gap이다.

Workload/token 값은 추천일 뿐 별도 승인 전에는 실행값이 아니다. Resource cadence의 `interval=1s`, `maxGap=5s` 쌍은 이번 수정의 승인 계약이지만 runner default로 두지 않고 매 실행에 명시해야 한다. after 측정과 개별 branch Docker build는 PM integration branch까지 금지한다.

## Runner/evidence boundary

runner는 workload와 target 값을 하나도 default하지 않는다. 실행 시 `DATASET_ID`, `FIXTURE_RUN_ID`, execution ID, workload/cadence 8개 값, ADMIN/duty user ID와 runtime-only credential, numeric loopback `BASE_URL`, PostgreSQL DB/user, 실제 Compose project와 app/PostgreSQL/Redis service, 세 container의 full immutable ID/image ID, source commit provenance를 모두 명시해야 한다. credential과 token 원문은 report, console용 조건 파일, classification에 기록하지 않는다.

fixture write 전에 workload parsing, app/PostgreSQL/Redis immutable identity, DB identity, numeric loopback published port, canonical actual-project lock 이후 continuity, quiet DB boundary, ADMIN/duty login identity와 token TTL을 모두 통과해야 한다. 그 뒤 execution 전용 campus를 create-only 생성하며, 기존 row 삭제나 reset/config/extension/Flyway/Docker lifecycle 변경은 하지 않는다.

측정 구간의 write는 새 fixture campus의 membership/duty/account/charge 생성과 report 파일 생성뿐이다. read는 login/`users/me` 및 correctness HTTP, Docker inspect/stats, PostgreSQL identity/activity/planner/table-maintenance/counter/선택적 `pg_stat_statements`, synthetic `EXPLAIN (ANALYZE, BUFFERS)`로 제한한다. EXPLAIN은 production plan 또는 독립 최적화 기여 증거가 아니며 #194 전달용 supporting evidence다.

k6는 16 cases를 frontend 순서로 한 iteration 안에서 실행한다. warmup은 승인된 shared iterations, measured는 승인된 constant VUS로 분리하고 warmup 뒤 measured ADMIN JWT를 새로 발급한다. summary는 direct/`values` shape 모두에서 case별 동일 count, failure rate/passes/fails 수학, Trend count, avg/median/p50/p95/p99/max 순서, throughput을 fail-closed 검증한다. DB counters는 decimal string을 `BigInt`로 계산하고, planner/maintenance/activity와 선택적 pgss availability/reset/dealloc continuity, app/PostgreSQL/Redis CPU/RAM sample coverage를 별도 검증한다.

Docker Desktop의 `MemUsage`는 `499.7MiB`, `7.653GiB`처럼 표시 정밀도에서 반올림된 관측치다. Resource evidence는 이를 exact byte 한 점으로 만들지 않는다. `memoryUsed`와 `memoryLimit`은 원본 `displayed`와 가능한 inclusive integer-byte `minimumBytesInclusive`/`maximumBytesInclusive` decimal-string 범위를 저장하고, `memoryPercent`는 원본 `displayed`와 반올림 구간의 exact numerator/denominator를 저장한다. Validator는 이 스키마를 원본 표시값에서 재계산하고, safe magnitude·positive limit·used≤limit과 가능한 used/limit ratio 구간이 MemPerc rational 구간과 겹치는지를 `BigInt`로 fail-closed 검증한다. 기존 scalar `memoryUsedBytes`, `memoryLimitBytes`, `memoryPercent` 수치 계약은 false precision을 피하기 위해 사용하지 않는다.

Resource cadence는 nominal requested interval과 approved maximum gap을 분리한다. `DOCKER_STATS_SAMPLING_INTERVAL_SECONDS=1`과 `DOCKER_STATS_MAX_GAP_SECONDS=5`를 runtime에 각각 명시하고 run conditions와 validation output에 둘 다 기록한다. Blocking `docker stats --no-stream` 뒤에는 고정 sleep을 추가하지 않고 즉시 다음 capture를 시작하며, validator는 timestamp monotonicity와 measured-window coverage를 유지한 채 인접 gap을 별도 5초 gate로 검증한다. maximum gap 누락·비정상 값·nominal interval 미만·실제 gap 초과는 fail-closed다.

공유 stack의 quiet snapshot은 경계 관찰일 뿐이다. 모든 DB/resource validator 뒤에는 app/PostgreSQL/Redis runtime, database, numeric loopback binding을 final snapshot으로 다시 비교하고, 이 final continuity를 통과한 뒤에만 classification을 기록한다. post-lock 이후 psql과 Docker stats는 mutable name이 아니라 승인된 full container ID를 사용한다. `measurementStatus`는 최대 `conditional-shared-stack`, `evidenceIntegrity`는 별도 검증 상태이며 `automaticAdoption=false`다. PM이 exclusive-use 전체 window와 evidence를 검토하기 전 baseline으로 채택할 수 없다.

PM이 확인한 현재 shared PostgreSQL에서는 `pg_stat_statements`가 unavailable이다. runner는 extension/config를 변경하지 않고 unavailable reason과 availability continuity를 보존하며, PostgreSQL decimal-string counter는 독립 query-count가 아닌 supporting evidence로만 기록한다.

## Rejected actual-before attempt B (2026-07-16)

`I193_BEFORE_20260716_B / I193_FIXTURE_20260716_B / EXEC193_BEFORE_20260716_B` 실행은 partial rejected evidence로만 보존한다. 사용자 승인 계정 2개 생성, 임시 ADMIN 로그인 gate, immutable runtime/DB/lock/quiet gate와 fresh fixture prepare까지 통과해 campus ID 17에 ACTIVE membership 1,000개와 charge item 35,000개를 COMMIT했다.

직후 dataset binding 조회가 `psql -c` 문자열 안의 `:'dataset_id'`를 치환할 것으로 잘못 가정해 PostgreSQL `syntax error at or near ":"`로 중단됐다. k6 warmup/measured 실행은 모두 0건이고 summary도 생성되지 않았으므로 baseline 또는 성능 수치로 사용할 수 없다. 외부 보안 cleanup trap이 임시 ADMIN을 USER로 복구했고 memory-only credential은 runner shell 종료와 함께 폐기됐다.

B namespace, DB rows, report directory는 삭제하거나 복구하지 않고 그대로 보존하며 절대 재사용하지 않는다. dataset binding은 raw shell interpolation 없이 `select-dataset-binding.sql`을 stdin으로 전달하고 `psql -v dataset_id=...`가 치환하도록 보정했다. 후속 실제 측정은 새 dataset/fixture/execution ID와 별도 승인 credential만 사용한다.

## Rejected actual-before attempt C (2026-07-16)

`I193_BEFORE_20260716_C / I193_FIXTURE_20260716_C / EXEC193_BEFORE_20260716_C` 실행도 partial rejected evidence로만 보존한다. dataset binding 보정, immutable runtime/DB/lock/quiet gate, fresh fixture prepare와 ADMIN/duty 인증을 통과했으며 campus ID 19에 ACTIVE membership 1,000개와 charge item 35,000개를 COMMIT했다.

k6 전 correctness preflight에서 member-detail expectation의 `createdAt desc, id desc` 검증이 실패했다. 실제 SQL 순서는 `...37.542110`, `...37.542109`, `...37.542108`의 마이크로초 내림차순이고 같은 timestamp 안에서는 ID 내림차순이었지만, validator가 `Date` millisecond로 모두 `...37.542`에 절삭해 서로 다른 instant를 동률로 오판했다. k6 warmup/measured 실행은 모두 0건이고 summary도 없으므로 baseline 또는 성능 수치로 사용할 수 없다. 외부 보안 cleanup trap은 임시 ADMIN을 USER로 복구했고 memory-only credential은 폐기됐다.

C namespace, DB rows, report directory는 삭제하거나 복구하지 않고 그대로 보존하며 절대 재사용하지 않는다. instant validator는 최대 9자리 fraction과 `Z`/offset을 strict parse해 lossless epoch nanoseconds로 비교하고, exact instant tie에서만 ID 내림차순을 요구한다. 날짜 전용 문자열, malformed timestamp, 유효하지 않은 달력 날짜, `24:00`, 9자리 초과 fraction은 fail-closed다. 설치된 k6의 정적 `inspect`로 BigInt module parse/instantiate 호환성도 확인하며, 후속 실제 측정은 새 namespace와 별도 승인 credential만 사용한다.

## Rejected actual-before attempt E (2026-07-16)

`I193_BEFORE_20260716_E / I193_FIXTURE_20260716_E / EXEC193_BEFORE_20260716_E` 실행은 partial rejected evidence로만 보존한다. fresh fixture는 campus ID 23에 ACTIVE membership 1,000개와 charge item 35,000개를 COMMIT했다. Warmup은 5 iterations와 80 HTTP request를 완료했고 HTTP failure는 0이었다.

실제 k6 v2 custom Rate summary의 무오류 shape는 `{"passes":0,"fails":5,"value":0}`이지만 validator가 존재하지 않는 `rate` 필드를 읽어 16개 case를 모두 거부했다. measured k6, measured DB/resource boundary, measured summary와 classification은 생성되지 않았다. 따라서 latency, throughput, baseline 또는 개선 성과로 채택하지 않으며 기존 `accepted=false`/`automaticAdoption=false` 경계를 바꾸지 않는다.

측정 계정 15016/15017은 모두 USER로 복구됐고 canonical performance lock 제거도 확인됐다. E namespace, DB rows, report directory는 삭제하거나 복구하지 않고 보존하며 절대 재사용하지 않는다. Validator는 direct metric과 `metric.values` wrapper를 모두 지원하되 k6 v2 semantics에 따라 `value=0`, `passes=0`, `fails=expected count`만 무오류로 허용하며 count mismatch, positive/nonfinite/malformed value를 계속 fail-closed 처리한다.

## Rejected actual-before attempt F (2026-07-16)

`I193_BEFORE_20260716_F / I193_FIXTURE_20260716_F / EXEC193_BEFORE_20260716_F` 실행은 partial rejected evidence로만 보존한다. Report 경로는 `build/reports/k6/issue-193/I193_BEFORE_20260716_F/I193_FIXTURE_20260716_F/EXEC193_BEFORE_20260716_F`이며, fresh fixture는 campus ID 25에 ACTIVE membership 1,000개와 charge item 35,000개를 COMMIT했다. Warmup은 5 iterations와 80 HTTP request를 완료했고 failure는 0이었다.

Measured load 시작 전 첫 resource normalization에서 정상 Docker Desktop 표시 `501.7MiB`를 exact integer bytes로 환산할 수 없다는 이유로 중단됐다. Measured k6 summary와 adoption/classification은 생성되지 않았으므로 warmup, latency, throughput, baseline 또는 개선 성과 수치로 채택하지 않는다.

측정 계정 15018/15019는 모두 USER로 복구됐고 canonical performance lock이 비어 있으며 실행 중인 k6가 없음을 확인했다. F namespace, DB rows, report directory는 삭제하거나 복구하지 않고 보존하며 절대 재사용하지 않는다. Resource normalizer는 scalar byte 값을 만들지 않고 표시 정밀도의 inclusive integer-byte 범위와 MemPerc rational 구간을 보존하도록 보정했다.

## Rejected actual-before attempt G (2026-07-16)

`I193_BEFORE_20260716_G / I193_FIXTURE_20260716_G / EXEC193_BEFORE_20260716_G` 실행은 partial rejected evidence로만 보존한다. Fresh fixture는 campus ID 27에 ACTIVE membership 1,000개와 charge item 35,000개를 COMMIT했다. Warmup은 5 iterations와 80 HTTP request를 failure 0으로 완료했다. Measured phase는 16 cases 각각 request count 239, custom failure value 0이었고 resource sample 90개가 `2026-07-16T04:54:06.025Z`부터 `2026-07-16T04:57:16.941Z`까지 수집됐다.

Measured summary, counter-after, measurement-state-after, PostgreSQL-after evidence는 존재하지만 resource validator에서 중단되어 runtime-final과 adoption/classification은 생성되지 않았다. 관찰된 sample gap은 최소 1.869초, 최대 4.807초였다. 기존 runner가 `DOCKER_STATS_SAMPLING_INTERVAL_SECONDS=1`을 maximum gap으로 해석하면서 blocking `docker stats --no-stream` 뒤에 0.5초 sleep까지 추가해, 실제 1.37~4.31초 capture overhead가 있는 환경에서 1초 validation gate를 구조적으로 만족할 수 없었다.

측정 계정 15020/15021은 모두 USER로 복구됐고 canonical lock free와 running k6 없음이 확인됐다. G namespace, DB rows, report는 삭제하거나 복구하지 않고 보존하며 절대 재사용하지 않는다. G의 measured 요청 수, latency, throughput, resource 수치는 baseline 또는 개선 성과로 채택하지 않는다. Fresh H만 별도 승인 후 사용할 수 있다.
