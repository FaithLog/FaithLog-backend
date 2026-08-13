# FaithLog Cloud Monitoring 관측 계약

## 목적

일반 `ERROR` 로그 급증 정책과 별도로 DB 연결 풀, 스케줄러, 외부 서비스, 인증 실패를 원인별로 구분한다. 백엔드는 `FAITHLOG_OBSERVABILITY` prefix의 bounded event만 남기며 이메일, 사용자 ID, 토큰, 인증번호, IP, R2 object key, provider 응답 원문은 기록하지 않는다.

## Runtime 설정

```text
FAITHLOG_OBSERVABILITY_ENABLED=true
FAITHLOG_DB_POOL_SAMPLE_DELAY_MS=60000
FAITHLOG_REDIS_PROBE_DELAY_MS=60000
```

DB와 Redis sampler는 기존 Spring scheduler가 활성화된 운영 revision에서 1분마다 실행한다. 계측을 끄면 no-op event port가 대신 주입되어 API, scheduler, email, FCM, R2 bean 기동 계약은 유지된다.

## Event 필터

모든 필터에는 아래 Cloud Run 범위를 함께 사용한다.

```text
resource.type="cloud_run_revision"
resource.labels.service_name="faithlog"
```

| 대상 | 추가 `textPayload` 필터 | 승인 알림 기준 |
|---|---|---|
| Hikari timeout | `"FAITHLOG_OBSERVABILITY event=DB_POOL_TIMEOUT"` | 1건 즉시 |
| Hikari pending | `"FAITHLOG_OBSERVABILITY event=DB_POOL_PENDING"` | 2분 지속 |
| Hikari 90% | `"FAITHLOG_OBSERVABILITY event=DB_POOL_HIGH_UTILIZATION"` | 5분 지속 |
| Scheduler 실패 | `"FAITHLOG_OBSERVABILITY event=SCHEDULER_FAILURE"` | 1건 즉시 |
| Scheduler 성공 | `"FAITHLOG_OBSERVABILITY event=SCHEDULER_SUCCESS"` | job별 예정 주기 + 10분 absence |
| Upstash | `"event=EXTERNAL_SERVICE_FAILURE service=UPSTASH_REDIS"` | 3건/5분 |
| Brevo | `"event=EXTERNAL_SERVICE_FAILURE service=BREVO"` | 3건/10분 |
| FCM | `"event=EXTERNAL_SERVICE_FAILURE service=FCM"` | transient 5건/10분 |
| R2 | `"event=EXTERNAL_SERVICE_FAILURE service=CLOUDFLARE_R2"` | 3건/10분 |
| 로그인 | `"event=AUTH_FAILURE flow=LOGIN"` | 20건/5분 |
| Refresh | `"event=AUTH_FAILURE flow=REFRESH_TOKEN"` | 20건/5분 |
| 이메일 인증 | `"event=AUTH_FAILURE flow=EMAIL_VERIFICATION"` | 20건/10분 |

FCM의 invalid/unregistered token 같은 영구 실패는 event에서 제외한다. Brevo는 provider 발송 실패만 집계하고 Cloud Tasks queue depth 정책과 함께 판단한다. Redis는 원문 command 실패를 기록하지 않고 독립 PING probe 실패를 집계한다.

## Scheduler absence

고정 지연 1분 작업은 마지막 성공 후 11분, 일일 cron 작업은 예정 시각 후 10분을 기준으로 한다. `job` 값은 코드에 고정된 다음 값만 허용한다.

```text
poll-auto-create
coffee-poll-close
data-retention-cleanup
fcm-token-cleanup
devotion-missing
poll-missing
payment-unpaid
pending-notification-reprocess
```

Cloud Run revision 교체 시간에는 old/new revision 로그가 겹칠 수 있으므로 metric은 service 단위로 합산하고 revision 이름을 알림 조건에 고정하지 않는다.

## 운영 확인

1. 배포 revision에서 `FAITHLOG_OBSERVABILITY_ENABLED=true`를 확인한다.
2. Logs Explorer에서 scheduler success와 DB sampler event를 확인한다.
3. 로그 기반 counter metric을 event별로 생성한다.
4. 승인 임계값으로 Alerting policy를 만들고 `FaithLog Admin` 이메일 채널을 연결한다.
5. 정책 문서에는 event filter, 확인할 provider 화면, rollback 기준을 기록한다.
