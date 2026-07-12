# Issue #157 보안 감사 결과와 후속 후보

## 1. 결론

기준 커밋 `d9d9f2506ddf50ce0a36d6ceada3b0768cfc8c81`에서 21개 Controller의
80개 endpoint, 4개 `permitAll` application endpoint, 76개 authenticated endpoint와 관련
service authorization guard를 대조했다.

| 분류 | Critical | High | Medium | Low | 합계 |
| --- | ---: | ---: | ---: | ---: | ---: |
| confirmed finding | 0 | 0 | 1 | 0 | 1 |
| hardening candidate(취약점 finding과 분리) | 0 | 0 | 0 | 3 | 3 |

- confirmed finding: 1개, 독립 코드 검증 완료
- false positive/의도된 정책: 7개
- 운영 콘솔 또는 동적 검증이 필요한 영역: 12개
- 코드, 설정, DB, Flyway, 운영 인프라 변경: 0개
- 새 수정 Issue/PR: 0개
- focused security regression: 19 tests / 0 failures / 0 errors / 0 skipped

## 2. Confirmed finding

### F-157-01 마지막 active service ADMIN이 본인 탈퇴로 보호를 우회할 수 있음

- Severity: **MEDIUM**
- Confidence: **10/10**
- Status: **VERIFIED** (독립 검증 포함)
- CVSS 3.1 참고값: `AV:N/AC:L/PR:H/UI:N/S:U/C:N/I:N/A:H` = 4.9
- CWE: CWE-862 Missing Authorization; 보조 CWE-841/CWE-840 business workflow enforcement
- OWASP: Top 10 2021 A01 Broken Access Control; API5:2023 Broken Function Level Authorization;
  API6:2023 Unrestricted Access to Sensitive Business Flows
- ASVS 5.0: V2 Validation and Business Logic, V8 Authorization의 역할/민감 workflow 불변조건
- 영향 경계: Cloud Run application → service ADMIN control plane

#### 증거

- `AccountWithdrawalCommandService.java:37-57`은 active 상태, 현재 비밀번호, 확인 문구를 검사한
  뒤 membership/FCM/session을 정리하고 soft delete하지만 `UserRole.ADMIN`과 active ADMIN 수를
  검사하지 않는다.
- `AdminUserManagementService.java:53-63`은 role 강등 경로에서
  `countByRoleAndIsActiveTrue(ADMIN) <= 1`을 검사해 마지막 ADMIN을 보호한다.
- `User.java:58-68`과 `SignupCommandService.java:24-32`에 따라 새 가입자는 항상 `USER`다.
- `AdminAccessPolicy.java:12-15`는 active ADMIN만 service admin API를 사용할 수 있게 한다.
- `docs/decision-log.md:13-18`은 #156에서 이 공백을 동작 변경 없이 후속 보안 감사로 넘겼음을
  명시한다.

#### 재현 전제와 공격/실패 시나리오

1. active service ADMIN이 정확히 1명 남아 있다.
2. 그 ADMIN의 유효한 access token/session과 현재 비밀번호를 보유하고 있다.
3. `DELETE /api/v1/users/me`에 정확한 확인 문구를 제출한다.
4. 서버는 membership, FCM token, refresh session을 정리하고 access JTI를 blacklist한 뒤 사용자를
   inactive/anonymized 상태로 바꾼다.
5. active ADMIN 수가 0이 된다. 새 가입은 USER만 생성하고 role 변경 API도 active ADMIN을 요구하므로
   일반 API로 복구할 수 없다.

토큰만 탈취한 공격자는 현재 비밀번호를 추가로 알아야 하므로 계정 탈취 즉시 악용되는 경로는
아니다. 영향도 전체 API 중단이 아니라 service 관리자 기능의 운영 잠금이며, campus manager가
보유한 제한된 campus 기능은 계속 동작할 수 있다.

#### 현재 방어

- valid/current JWT와 active user 확인
- 현재 비밀번호 재입력
- 정확한 `회원탈퇴` 확인 문구
- 탈퇴 시 tokenVersion 증가, refresh 전부 삭제, 현재 access blacklist, FCM/membership 비활성화
- role 강등 API에는 마지막 ADMIN 보호 존재

이 방어들은 오동작과 token-only 공격을 줄이지만, 마지막 ADMIN 불변조건을 탈퇴 transition에
적용하지는 않는다.

#### 권장 수정안(아직 승인/구현되지 않음)

별도 수정 Issue 한 건으로 다음을 제안한다.

1. service ADMIN의 탈퇴 전 active ADMIN 수를 검사하고 마지막 1명이면 `409`로 거절한다.
2. role 강등과 탈퇴가 동일한 shared policy/transactional serialization을 사용하게 해 두 ADMIN의
   동시 강등/탈퇴로 0명이 되는 경합도 함께 차단한다.
3. 마지막 ADMIN 탈퇴 Controller/service 테스트, 두 최종 ADMIN transition 동시성 테스트,
   기존 USER 탈퇴 회귀 테스트를 추가한다.
4. ErrorCode/사용자 메시지는 제품 동작이므로 구현 전에 PM 승인을 받는다.

제안 Issue 제목: `[Security] 마지막 active service ADMIN 탈퇴 및 동시 전이 보호`

## 3. Hardening 후보(confirmed vulnerability finding과 분리)

### H-157-01 production image non-root 사용자

- Severity class: LOW hardening
- Evidence: `Dockerfile` runtime stage에 `USER`가 없어 기본 root로 실행된다.
- 현재 영향: 애플리케이션 RCE 또는 JVM escape 전제가 먼저 필요하고 Cloud Run sandbox가 추가
  경계를 제공하므로 독립 취약점으로 승격하지 않았다.
- 제안: 최소 권한 UID/GID 생성, jar/read-only 경로 권한 조정, Cloud Run smoke 검증.

### H-157-02 GitHub Actions immutable SHA pin

- Severity class: LOW supply-chain hardening
- Evidence: 두 workflow가 `actions/*`, `gradle/actions/*`, `docker/*` action을 major tag로 참조한다.
- 현재 방어: workflow `permissions: contents: read`, Gradle wrapper validation, PR/push branch 제한.
- 제안: action commit SHA pin과 Renovate/Dependabot update 정책을 함께 적용.

### H-157-03 repository secret scanner policy file

- Severity class: LOW preventive hardening
- Evidence: 실제 key 형식 후보는 발견되지 않았지만 `.gitleaks.toml`/`.secretlintrc`가 없다.
- 현재 방어: `.gitignore`, example placeholder, CI/test dummy 값, 문서의 secret manager 정책.
- 제안: provider별 allowlist와 test fixture 예외가 명시된 scanner를 CI에 추가.

이 세 항목은 제품 보안 동작을 바꾸므로 이번 이슈에서 파일을 수정하거나 Issue를 생성하지 않는다.
필요하면 LOW hardening 묶음 Issue 한 건으로 PM에게 제안한다.

## 4. False positive와 의도된 정책

| 후보 | 판정 | 근거 |
| --- | --- | --- |
| Cloud Run `--allow-unauthenticated` | false positive | Cloud Run IAM 통과 정책이며 Spring Security가 4개 외 전체를 인증함 |
| stateless API의 CSRF disable | false positive | cookie session이 아닌 Authorization Bearer 사용; CORS는 인증 대체 수단이 아님 |
| service ADMIN의 campus membership 없는 접근 | 의도됨 | 승인 정책상 전역 ADMIN override |
| 마지막 campus manager를 MEMBER로 내릴 수 있음 | 의도됨 | decision log가 명시적으로 허용; service ADMIN 복구 가능 |
| 익명 poll의 `poll_responses.user_id` 저장 | 의도됨/방어 확인 | 중복·수정·미응답 계산용이며 결과 assembler는 respondent identity를 만들지 않음 |
| FCM 동일 token의 다른 사용자 ownership 이전 | 의도됨/방어 확인 | 공유 기기 알림 누출을 막기 위해 기존 active owner를 비활성화 |
| Redis 알림 오류 시 알림 미전송 | 의도된 fail-closed | scheduled lock은 empty, dedup reserve는 false로 종료해 중복 방어 없이 보내지 않음 |

## 5. 인증·인가 방어 확인

| 항목 | 확인 결과 |
| --- | --- |
| access token | signature, expiration, `tokenType=ACCESS`, JTI, userId, role, sessionId, tokenVersion 확인 |
| refresh token | `tokenType=REFRESH`, Redis current JTI, 14일 TTL, rotation, old token 거절+session 삭제 |
| logout | 현재 access JTI blacklist, session refresh 삭제, 선택 기기 FCM user scope |
| role 변경 | service/campus role 변경 시 tokenVersion 증가, filter가 active DB version 재확인 |
| account deletion | 익명화, active=false, tokenVersion 증가, sessions/FCM/memberships 정리; F-157-01 제외 |
| FCM | `tokenId+userId` 삭제, same client/token active ownership 정리, inactive/stale 발송 제외 |
| tenant-campus | 주요 객체를 campus/parent/owner와 결합 조회하고 mismatch는 403/404 처리 |
| anonymous poll | 내부 userId는 유지하지만 anonymous result의 users map/respondents는 빈 값 |
| Scheduler | 외부 HTTP endpoint 없음, service 직접 호출, lock/dedup Redis 장애 fail-closed |

## 6. OWASP 매핑

### 6.1 OWASP API Security Top 10 2023

| 항목 | 감사 결과 |
| --- | --- |
| API1 BOLA | 18개 식별자 범주를 parent campus/poll/owner와 대조; confirmed BOLA 없음 |
| API2 Broken Authentication | JWT type/version/blacklist와 refresh rotation 확인; edge rate limit/MFA는 미확인 |
| API3 Broken Object Property Level Authorization | DTO 응답과 익명 poll/account admin metadata 분리 확인 |
| API4 Unrestricted Resource Consumption | 이 감사의 공격성/부하 검증 제외; Cloud Run/WAF limit 미확인 |
| API5 BFLA | 관리자/campus/duty guard 대조; F-157-01 confirmed |
| API6 Sensitive Business Flows | 탈퇴/role/청구/알림 workflow 검토; F-157-01 confirmed |
| API7 SSRF | user-controlled URL outbound surface 미발견 |
| API8 Security Misconfiguration | prod docs off, Redis TLS/env, Flyway validate 확인; 콘솔 값 미확인 |
| API9 Improper Inventory Management | 80개 endpoint inventory와 4개 permitAll 대조 완료 |
| API10 Unsafe Consumption of APIs | FCM 오류 분류/token 비활성화 확인; Firebase IAM 미확인 |

### 6.2 OWASP ASVS 5.0

| ASVS 영역 | 적용 증거/결과 |
| --- | --- |
| V2 Validation and Business Logic | 상태 전이/role hierarchy/owner 검증; 마지막 ADMIN 탈퇴 invariant F-157-01 |
| V4 API and Web Service | 80개 REST mapping, request validation, public/authenticated inventory |
| V6 Authentication | BCrypt, active user, credential 오류 통합 |
| V7 Session Management | Redis allowlist/blacklist, TTL, logout, refresh rotation |
| V8 Authorization | trusted service layer의 service/campus/duty/owner guard; 18개 identifier 범위 |
| V9 Self-contained Tokens | JWT signature/type/JTI/sessionId/tokenVersion/expiration |
| V12 Secure Communication | Cloud Run HTTPS, Supabase/Upstash/Firebase TLS 문서; 실제 콘솔 미확인 |
| V13 Configuration | profile split, secret env, prod Swagger off, Flyway validate |
| V14 Data Protection | 탈퇴 익명화, 계좌 DTO 분리, anonymous poll identity hiding |
| V16 Security Logging and Error Handling | 공통 API error, scheduler job 결과/실패 log; 운영 sink/alert 미확인 |

참조:

- <https://owasp.org/www-project-application-security-verification-standard/>
- <https://owasp.org/API-Security/editions/2023/en/0x11-t10/>
- <https://owasp.org/Top10/en/A01_2021-Broken_Access_Control/>

## 7. 운영 콘솔/동적 확인 체크리스트

저장소 증거만으로 판정하지 않은 항목이다.

- [ ] Cloud Run ingress가 승인된 범위인지, 예상치 않은 revision/도메인이 없는지
- [ ] Cloud Run service account 최소 권한과 Secret Manager accessor 범위
- [ ] Cloud Run request/log에 Authorization, refresh token, FCM token, 계좌번호가 남지 않는지
- [ ] Cloud Armor/API gateway/rate limit 또는 login abuse alert 존재 여부
- [ ] service ADMIN MFA/step-up 인증 정책 여부
- [ ] Supabase direct/pooler TLS, network restriction, DB role 최소 권한, backup/restore, audit log
- [ ] Supabase RLS 사용 여부; 미사용 시 application-only tenant guard를 운영 정책으로 명시했는지
- [ ] Upstash TLS, ACL/credential rotation, eviction/availability alert, keyspace 접근 범위
- [ ] Firebase service account 최소 권한, key rotation, audit log, FCM data retention
- [ ] Artifact Registry vulnerability scan, image provenance/signing, deploy 승인 주체
- [ ] GitHub branch protection과 workflow/CODEOWNERS 보호
- [ ] 실제 prod에서 Swagger/API docs 비노출과 actuator health 정보 최소화

## 8. 후속 Issue 제안(생성하지 않음)

| 우선순위 | 제안 | 묶음 기준 |
| --- | --- | --- |
| Medium | `[Security] 마지막 active service ADMIN 탈퇴 및 동시 전이 보호` | F-157-01 단일 control-plane invariant |
| Low | `[Security Hardening] 이미지 최소 권한·CI action pin·secret scanner` | CI/container/repository supply-chain 경계 |
| 운영 확인 | `[Security Ops] Cloud Run/Supabase/Upstash/Firebase 콘솔 체크` | 저장소 밖 운영 신뢰 경계 |

사용자 승인 전에는 어떤 Issue도 생성하지 않는다.

## 9. 감사 한계와 면책

이 감사는 저장소 정적 분석과 안전한 기존 테스트 검증을 기반으로 한다. 운영 credential, 콘솔 IAM,
실제 네트워크 경로, 배포 이미지, 모바일 secure storage, 공격성 penetration test를 확인하지 않았다.
AI 보조 보안 감사는 전문 침투 테스트를 대체하지 않으며, 개인정보·계좌정보를 처리하는 운영
서비스는 정기적으로 독립 보안 전문가의 검토와 승인된 동적 테스트를 받아야 한다.
