# Issue #158 JWT와 세션 수명주기 보안 감사 결과

## 1. 결론

기준 커밋 `634d19c7fea6753a37d64490b4dc97efe867eb43`에서 production/config/schema
47개 파일, focused test 8개 파일, API 10개, Redis 인증 흐름 5개를 대조했다.

| 분류 | Critical | High | Medium | Low | 합계 |
| --- | ---: | ---: | ---: | ---: | ---: |
| confirmed finding | 0 | 0 | 1 | 0 | 1 |

- confirmed finding: 1개, 독립 코드 검증 완료
- false positive/의도된 정책: 7개
- 운영 또는 제품 정책 확인 필요: 7개
- 코드, production/test/config/DB/Flyway, 운영 인프라 변경: 0개
- 새 수정 Issue/PR/push: 0개
- focused test: 2개 명령, 합계 33 tests / 0 failures / 0 errors / 0 skipped
- Docker 사용: 없음

F-158-01은 같은 old refresh의 병렬 replay로 복수 access token이 최대 1,800초 유효해지는 것에
더해, 공격자 SET이 마지막이면 공격자 refresh가 current JTI로 남아 정상 client의 다음 mismatch
삭제 전까지 최장 1,209,600초(14일) 동안 조건부로 회전 가능한 session 지속 영향을 포함한다.

## 2. Confirmed finding

### F-158-01 refresh token 회전의 비교와 교체가 원자적이지 않아 동시 replay가 모두 성공함

- Severity: **MEDIUM**
- Confidence: **10/10**
- Status: **VERIFIED** (독립 검증 포함)
- CWE: CWE-362 Concurrent Execution using Shared Resource with Improper Synchronization;
  보조 CWE-294 Authentication Bypass by Capture-replay
- OWASP: API2:2023 Broken Authentication
- ASVS 5.0.0: `v5.0.0-2.3.3`, `v5.0.0-2.3.4`, `v5.0.0-7.2.4`,
  `v5.0.0-7.4.1`; OAuth refresh control intent로 `v5.0.0-10.4.5`
- 영향 경계: public `POST /api/v1/auth/refresh` → Redis refresh allowlist → access token 발급

#### 증거

- `RefreshTokenRotationService.java:47-50`은 Redis current JTI 일치 여부를 먼저 읽고, 성공한
  요청을 별도 발급 단계로 넘긴다.
- `RedisRefreshTokenStore.java:28-30`의 검사는 단순 GET/equals이고,
  `RedisRefreshTokenStore.java:23-24`의 새 JTI 저장은 별도 SET이다.
- `AuthTokenIssuanceSupport.java:25-35`는 새 access/refresh token을 만든 뒤 SET한다.
- `JwtProvider.java:58-70,81-94`는 각 요청에 서로 다른 access JTI를 발급한다.
- `JwtAuthenticationFilter.java:60-89`는 access token 검증 때 그 session의 current refresh JTI를
  확인하지 않으므로 두 access token 모두 유효하다.
- `AuthRefreshControllerTest.java:45-83`은 정상 회전과 순차 old-token reuse만 검증하고 동시 요청은
  검증하지 않는다.

#### 재현 전제와 공격 시나리오

1. 공격자가 아직 만료되지 않았고 Redis current JTI와 일치하는 refresh token을 탈취하거나 보유한다.
2. 같은 refresh token으로 두 요청 A/B를 짧은 시간 안에 병렬 전송한다.
3. Redis 실행 순서가 `A GET(old) → B GET(old) → A SET(newA) → B SET(newB)`가 된다.
4. A와 B 모두 200 응답으로 서로 다른 access/refresh token pair를 받는다.
5. 마지막 SET의 refresh token 하나만 allowlist에 남지만, 두 access token은 각각 서명/만료/version
   검증을 통과해 기본 최대 1,800초 동안 API에 사용할 수 있다.
6. 공격자 요청의 SET이 마지막이면 공격자 refresh가 current JTI가 된다. 정상 client가 loser refresh를
   다시 제출해 mismatch 처리로 session key를 삭제하기 전까지 공격자는 이 refresh를 최장
   1,209,600초(14일) 동안 다시 회전할 수 있다.

이는 단순 가용성 race가 아니라 single-use refresh rotation의 replay 방어를 우회해 하나의 refresh
credential에서 복수 access bearer를 발급하는 경로다.

#### 영향

- 탈취한 refresh token을 legitimate refresh와 경쟁시켜 추가 access token을 확보할 수 있다.
- Redis에 마지막으로 기록되지 않은 loser refresh는 재사용할 수 없지만 이미 발급된 access는
  blacklist나 `tokenVersion` 변경이 없으면 만료까지 살아 있다.
- 공격자 refresh가 마지막 SET의 승자라면 공격자는 access 만료 뒤에도 current refresh를 다시 회전할
  수 있다. 최장 경계는 refresh TTL 14일이지만 정상 client의 다음 loser refresh 제출은 mismatch로
  session key를 삭제하므로 실제 지속 시간은 client 후속 동작과 요청 순서에 좌우된다.
- attacker와 정상 client의 행위를 server가 access JTI만으로 구분할 수 없다.

#### 현재 방어

- JWT signature, `tokenType=REFRESH`, expiration, 필수 claim 검증
- Redis current JTI와 active user 확인
- UUID 기반 session/JTI
- 순차 old refresh reuse는 current session 삭제 후 401
- access token은 active user와 DB `tokenVersion`을 매 요청 재확인

이 방어는 순차 replay를 막지만 check-then-set 사이 동시 요청을 한 번의 승자로 제한하지 않는다.

#### 권장 수정안(승인/구현되지 않음)

별도 수정 Issue 한 건으로 다음을 제안한다.

1. `expectedRefreshJti` 비교와 `newRefreshJti + TTL` 교체를 Redis Lua 또는 동등한 CAS 원자 연산
   하나로 만든다.
2. CAS 승자만 access/refresh token response를 만들거나 반환한다. loser는 token을 응답하지 않는다.
3. CAS mismatch와 이미 사용된 refresh 재제출의 session revoke 정책을 함께 설계해 legitimate 동시
   refresh와 theft replay의 처리 결과를 명시한다.
4. 실제 Redis adapter 동시성 테스트와 Controller/service 회귀 테스트에서 같은 old refresh의
   성공 응답이 정확히 1개임을 검증한다.
5. ErrorCode/응답/재사용 감지 key/lock TTL은 보안 제품 동작이므로 구현 전에 PM 승인을 받는다.

제안 Issue 제목: `[Security] Refresh Token Rotation 원자성 및 동시 재사용 차단`

## 3. #157 finding 연관 영향

F-157-01 마지막 active service ADMIN 탈퇴 보호 공백은 이번 finding으로 다시 만들지 않는다.
회원탈퇴의 session 영향만 대조한 결과, 탈퇴가 승인되면 다음 방어는 확인됐다.

- `users.is_active=false`, `tokenVersion++`로 과거 access 전체 거절
- `auth:refresh:{userId}:*` 삭제로 refresh session 전체 제거
- 현재 access JTI blacklist
- 모든 active FCM token과 campus membership 비활성화

따라서 F-157-01의 문제는 탈퇴 진입 전 마지막 ADMIN 불변조건이며, 탈퇴 후 token/session cleanup
누락이 아니다.

## 4. 의도된 정책과 false positive

| 후보 | 판정 | 근거 |
| --- | --- | --- |
| `alg=none` 또는 RSA/EC algorithm confusion | false positive | JJWT 0.12.6 signed parser와 고정 `SecretKey` type/length 검증이 거절, `unsecured()` 미사용 |
| access token을 refresh endpoint에 사용 | false positive | `tokenType=REFRESH` parser가 거절 |
| refresh token을 Bearer access로 사용 | false positive | `tokenType=ACCESS` filter가 거절, 기존 401 테스트 존재 |
| Redis 장애 시 access 인증 허용 | false positive | blacklist 조회 예외 시 filter가 principal을 만들지 않아 401 |
| Redis에 raw refresh token 저장 | false positive | key에는 user/session, value에는 `refreshJti`만 저장 |
| 같은 FCM token의 다른 user ownership 이전 | 의도된 방어 | 이전 active owner를 비활성화해 공유 기기의 cross-account 알림 누출을 차단 |
| REST Docs의 token/password 패턴 | production 노출 false positive, hygiene 후보 | 97개 test snippet 중 38개에서 패턴 확인. test profile dummy/test JWT이며 ignored `build/`에만 존재하고 tracked 파일 0개 |

위 표는 결론의 false positive/의도된 정책 7개에만 집계한다. logout 후 같은 session의 과거 access
token 잔존은 false positive나 승인 정책으로 집계하지 않고 U-158-03 정책 미확인 한 건으로만 센다.

logout의 범위를 강화하기로 결정하면 `auth:session:revoked:{userId}:{sessionId}` 같은 session-level
denylist 또는 session별 access JTI 추적을 검토할 수 있다. 현재 정책 변경은 하지 않았다.

## 5. 운영 또는 정책 미확인 항목

| ID | 확인 필요 | 저장소에서 확인한 사실 | 확인 주체/증거 |
| --- | --- | --- | --- |
| U-158-01 | production JWT secret entropy와 rotation | prod는 기본값 없는 env 참조지만 `JwtProvider`가 blank/최소 entropy를 fail-fast하지 않음. SHA-256은 짧은 secret의 entropy를 늘리지 않음 | Secret Manager 값의 생성 방식·길이·rotation 이력, 값 자체는 보고 금지 |
| U-158-02 | issuer/audience 정책과 환경/서비스 key 재사용 | `iss`/`aud` 발급·검증 없음 | Cloud Run 환경별 key 분리와 다른 resource server/issuer 존재 여부 |
| U-158-03 | logout이 현재 access 1개인지 현재 session 전체인지 | 구현/결정은 현재 access JTI + refresh session 삭제. proactive refresh로 access 유효기간이 겹치는지는 미확인 | PM의 사용자-facing logout 의미와 frontend refresh 선행시간 |
| U-158-04 | Upstash TLS/ACL/eviction/timeout/retry/alert | prod example은 password+SSL, code는 예외 시 fail-closed | Upstash console과 Cloud monitoring |
| U-158-05 | Cloud Run request/error log redaction | application logger에 credential 기록 경로 없음 | load balancer/Cloud Run/APM에서 Authorization·request body 미수집 증거 |
| U-158-06 | 모바일 token 저장·삭제 | REST Docs는 secure storage를 요구 | iOS Keychain/Android Keystore/SecureStore 구현과 logout/withdraw cleanup |
| U-158-07 | login/refresh abuse control과 ADMIN MFA | 저장소에 전용 rate limit/account abuse control/MFA 없음; #157에서도 운영 미확인 | Cloud Armor/API gateway/alert 및 service ADMIN 인증 정책 |

U-158-01은 실제 secret이 약하다는 증거가 없어 confirmed finding이 아니다. U-158-02도 단일 service,
환경별 독립 key라면 즉시 token substitution 경로가 없다. 둘 다 운영 증거 없이 취약점으로 승격하지
않는다.

## 6. 401/403와 오류 노출 결론

- signature/type/expiration/JTI blacklist/active user/tokenVersion 실패는 `AUTH_UNAUTHORIZED` 401이다.
- 인증 뒤 service/campus role 부족은 domain-specific 403이다.
- owner-scoped FCM token ID mismatch는 다른 사용자의 token 존재를 숨기는 404다.
- parser/Redis exception message나 token 값은 API error body에 echo되지 않는다.
- refresh Redis 장애는 성공하지 않지만 전용 auth 503 mapping이 없어 5xx 운영 형태는 동적 확인이
  필요하다.

## 7. 검증

### 7.1 실행한 테스트

첫 번째 명령:

```text
./gradlew test \
  --tests 'com.faithlog.user.controller.AuthRefreshControllerTest' \
  --tests 'com.faithlog.user.controller.AuthLogoutControllerTest' \
  --tests 'com.faithlog.user.controller.AuthLogoutFcmPersistenceTest' \
  --tests 'com.faithlog.user.controller.UserDeletionControllerTest' \
  --tests 'com.faithlog.global.security.RoleTokenInvalidationIntegrationTest' \
  --tests 'com.faithlog.notification.service.FcmTokenServiceTest' \
  --tests 'com.faithlog.user.service.AuthServiceTest'
```

결과: 7 classes, 22 tests / 0 failures / 0 errors / 0 skipped, `BUILD SUCCESSFUL`.

두 번째 명령:

```text
./gradlew test --tests 'com.faithlog.user.controller.AuthApiRestDocsTest'
```

결과: 1 class, 11 tests / 0 failures / 0 errors / 0 skipped, `BUILD SUCCESSFUL`.

### 7.2 범위 무결성

- Docker: 실행하지 않음
- production/test/config/DB/Flyway 변경: 0개
- 실제 secret/token/개인정보 값 기록: 0개
- push/PR/수정 Issue: 0개

## 8. 후속 Issue 후보(생성하지 않음)

| 우선순위 | 후보 | 상태 |
| --- | --- | --- |
| Medium | `[Security] Refresh Token Rotation 원자성 및 동시 재사용 차단` | F-158-01, PM 승인 전 생성 금지 |
| 정책 결정 후 Medium 후보 | `[Security] Logout 시 현재 session의 모든 access token 폐기` | U-158-03의 logout 의미를 PM이 session 전체로 승인할 때만 |
| hardening | JWT secret startup validation, issuer/audience, REST Docs masking | 운영 구조와 PM 보안 정책 확인 후 범위 결정 |

## 9. 감사 한계와 면책

이 감사는 저장소 정적 분석, 독립 코드 검증, 안전한 기존 focused test에 기반한다. 운영 credential,
Cloud Run/Upstash/Firebase console, 모바일 secure storage, 실제 네트워크 경로와 공격성 penetration
test를 확인하지 않았다. AI 보조 보안 감사는 전문 보안 감사나 침투 테스트를 대체하지 않으며,
개인정보와 인증 token을 처리하는 운영 서비스는 정기적인 독립 전문가 검토와 승인된 동적 테스트를
받아야 한다.
