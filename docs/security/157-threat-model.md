# Issue #157 FaithLog 위협 모델과 감사 인벤토리

## 1. 감사 기준선

- 감사 일자: 2026-07-12
- 기준 커밋: `d9d9f2506ddf50ce0a36d6ceada3b0768cfc8c81` (`origin/develop`)
- 작업 브랜치: `audit/157-threat-model-authorization-matrix`
- 범위: 읽기 전용 정적 감사, 기존 테스트를 이용한 안전한 검증, 문서화
- 제외: 운영 서버 스캔, 운영 콘솔 변경, credential 검증, 부하 테스트, 코드/DB/Flyway/배포 설정 변경
- 비밀정보 처리: 패턴의 존재 여부와 파일 경로만 검사하고 값은 수집하거나 기록하지 않음

감사 기준은 저장소의 `AGENTS.md`, `docs/codex/FAITHLOG_CODEX_HOOK.md`,
`docs/decision-log.md`, `docs/backend-implementation-policy.md`, GitHub Issue #157,
OWASP ASVS 5.0과 OWASP API Security Top 10 2023이다.

## 2. 시스템과 데이터 흐름

```text
모바일 앱
  └─ HTTPS + Bearer JWT
      └─ Cloud Run / Spring Boot
          ├─ JDBC/TLS ─ Supabase PostgreSQL
          ├─ TLS + password ─ Upstash Redis
          └─ Firebase Admin SDK/TLS ─ Firebase FCM ─ 모바일 앱

Cloud Run 내부 Scheduler
  └─ Poll/정리/알림 use case
      ├─ PostgreSQL 업무 데이터
      ├─ Redis lock/dedup
      └─ FCM 비동기 전송
```

애플리케이션은 Cloud Run 자체 IAM 인증 대신 공개 HTTPS ingress 위에서 Spring Security JWT를
사용하도록 문서화돼 있다. 따라서 `--allow-unauthenticated`는 애플리케이션 API를 public으로
바꾸는 설정이 아니라, Cloud Run 앞단 통과 후 `SecurityConfig`와 service guard가 최종 권한을
판정하게 하는 신뢰 경계다. 실제 Cloud Run ingress/IAM/Secret Manager 값은 저장소만으로 확인할
수 없다.

## 3. 신뢰 경계

| # | 경계 | 통과 데이터 | 공격 표면 | 저장소에서 확인한 방어 | 미확인 영역 |
| --- | --- | --- | --- | --- | --- |
| TB-1 | 모바일 앱 → Cloud Run | 자격증명, JWT/refresh token, 요청 DTO, FCM token | 위조 토큰, 입력 변조, IDOR/BOLA, 대입 공격 | HTTPS 전제, JWT 서명/만료/type, DTO 검증, service 객체 범위 검사 | 실제 ingress, WAF/rate limit, 모바일 안전 저장소 |
| TB-2 | Cloud Run 인증 필터 → service | `AuthenticatedUser`, service role | 오래된 role, 로그아웃 토큰 재사용 | access blacklist, `tokenVersion`, active-user DB 확인 | Redis 장애/지연 운영 지표 |
| TB-3 | service → Supabase PostgreSQL | 개인정보, 멤버십, 청구, 기도, 경건, 투표, 알림 로그 | tenant 혼합, 과다 노출, 변조 | `campusId`/owner 조합 조회, JPA 바인딩, Flyway, prod `ddl-auto=validate` | Supabase RLS/네트워크/백업/암호화 콘솔 설정 |
| TB-4 | Cloud Run → Upstash Redis | refresh JTI, access JTI blacklist, lock/dedup key | 세션 재사용, lock 우회, 가용성 저하 | raw token 미저장, TTL, 알림 lock/dedup fail-closed | Upstash ACL/TLS 인증서/rotation/alert 설정 |
| TB-5 | Cloud Run → Firebase FCM | FCM token, 제목/본문, 전달 결과 | 다른 사용자 기기 전송, token 유출 | active token 소유권 이전, owner-scoped 삭제, 90일 stale 제외, invalid token 비활성화 | Firebase IAM, 서비스 계정 권한, 콘솔 audit log |
| TB-6 | Scheduler → 업무 use case | 자동 Poll/정산/정리/알림 명령 | 중복 실행, 범위 초과 삭제/전송 | 내부 전용 호출, Redis lock/dedup, 트랜잭션, 에러 로깅 | Cloud Run 인스턴스 수와 실제 스케줄러 enable 값 |
| TB-7 | GitHub Actions/Docker → Cloud Run 이미지 | 소스, Gradle 의존성, 이미지 | 공급망 변조, secret 포함, root runtime | read-only workflow permission, wrapper validation, secret placeholder 정책 | branch protection, CODEOWNERS, Artifact Registry 서명/스캔 |

## 4. 보호 자산

| # | 자산 | 등급 | 저장/처리 위치 | 핵심 방어 |
| --- | --- | --- | --- | --- |
| A-1 | 이메일, 이름, 사용자 상태 | Restricted | `users`, API 응답 | 인증, campus/admin scope, 탈퇴 익명화 |
| A-2 | password hash | Restricted | `users.password_hash` | BCrypt, 응답 미노출, 탈퇴 시 사용 불가 hash 교체 |
| A-3 | access/refresh 식별자와 session ID | Restricted | JWT, Redis | 서명, type claim, JTI, rotation, blacklist, TTL |
| A-4 | service/campus role과 duty | Confidential | `users`, `campus_members`, `campus_duty_assignments` | active 상태, 계층 정책, `tokenVersion` 증가 |
| A-5 | 계좌번호/예금주/은행과 snapshot | Restricted | `payment_accounts`, `charge_items` | campus membership/manager/duty/owner 범위 |
| A-6 | 청구 금액과 납부 상태 | Restricted | `charge_items` | 본인 또는 manager 범위, 상태 전이 정책 |
| A-7 | 경건생활 일/주 기록 | Restricted | devotion tables | ACTIVE campus member 본인 범위, 관리자 missing 범위 |
| A-8 | 기도제목과 수정 이력 | Restricted | prayer tables | ACTIVE campus read, group/manager write, version 검사 |
| A-9 | 익명 투표 응답자 연결 | Restricted | `poll_responses.user_id` | 결과 DTO에서 익명 poll 응답자 map 미생성 |
| A-10 | 댓글/비익명 투표 응답자 | Confidential | poll tables | campus visibility, author/manager 수정 권한 |
| A-11 | FCM token과 notification log | Restricted | notification tables | token owner scope, active/stale 필터, 관리자 log scope |

보호 자산은 11개 범주로 분류했다. 계좌정보는 결제 카드정보는 아니지만 실제 이체에 쓰이는
식별정보이므로 개인정보와 동일하게 Restricted로 취급한다.

## 5. 객체 식별자 공격 표면

| 식별자 | 대표 자원 | 반드시 결합할 범위/소유권 |
| --- | --- | --- |
| `campusId` | 모든 campus tenant 데이터 | ACTIVE membership, campus manager, service ADMIN 또는 active COFFEE duty |
| `userId` | 관리자 사용자/멤버 청구/알림 대상 | service ADMIN 또는 같은 campus manager/duty 허용 범위 |
| `membershipId`, `campusMemberId` | 멤버 삭제/역할 변경 | path `campusId`와 같은 membership, requester 역할 계층 |
| `assignmentId` | COFFEE duty 해제 | 같은 `campusId`, `DutyType.COFFEE`, active assignment |
| `seasonId` | 기도 season | season에서 도출한 campus의 manager 권한 |
| `groupId` | 기도 group | group → season → campus 연쇄 범위 |
| `weekStartDate` | 경건/기도 주차 | 로그인 본인 또는 같은 campus 관리자, Monday 검증 |
| `pollId` | 투표/결과/응답 | 같은 `campusId`, visibility window, poll type별 관리자/duty |
| `templateId` | 투표 template | 같은 `campusId`, template manager/duty |
| `optionId`/`optionIds` | 투표 선택지 | 요청 `pollId`에 속하는 option 집합 |
| `responseId` | 내부 정산 응답 | poll에서 도출하며 외부 직접 변경 API 없음 |
| `commentId` | 투표 댓글 | 같은 `pollId`, author 또는 campus 관리자 |
| `paymentAccountId`, `accountId` | 납부 계좌 | 같은 campus/type, active/deleted, COFFEE owner 조건 |
| `chargeItemId` | 청구 | 본인+campus 또는 charge에서 도출한 manager campus |
| `tokenId` | FCM token | repository `id + authenticated userId` |
| `requestId`, `targetId` | notification log/filter | 같은 campus의 notification manager |
| JWT `jti`, `sessionId`, `refreshJti` | 세션 | token signature/type/user와 Redis current identifier |
| `clientInstanceId`, FCM `token` | 앱 설치/푸시 대상 | authenticated user에게 active ownership 이전 |

총 18개 식별자 범주를 확인했다. 단순 UUID/숫자 난이도에 의존하지 않고 service에서 tenant와
owner를 다시 결합하는지를 판정 기준으로 사용했다.

## 6. 감사 대상 인벤토리

### 6.1 HTTP surface

| 도메인 | Controller 수 | endpoint 수 |
| --- | ---: | ---: |
| global health | 1 | 1 |
| user/auth | 2 | 6 |
| campus | 2 | 12 |
| admin/dashboard | 2 | 6 |
| billing | 2 | 13 |
| devotion | 4 | 8 |
| poll | 4 | 19 |
| prayer | 2 | 11 |
| notification | 2 | 4 |
| 합계 | 21 | 80 |

`GlobalExceptionHandler`는 `@RestControllerAdvice`이며 endpoint/controller 합계에서는 제외했다.
전체 endpoint 상세와 권한 대조 결과는 `docs/security/157-api-authorization-matrix.md`에 둔다.

### 6.2 인증과 공통 예외

- `global/security/SecurityConfig.java`
- `global/security/JwtAuthenticationFilter.java`
- `global/security/JwtProvider.java`
- `global/security/RestAuthenticationEntryPoint.java`
- `global/security/AuthenticatedUser.java`
- `global/security/AccessTokenBlacklistChecker.java`
- `global/security/AccessTokenVersionChecker.java`
- `user/infrastructure/adapter/UserAccessTokenVersionChecker.java`
- `user/infrastructure/redis/RedisAccessTokenBlacklistStore.java`
- `user/infrastructure/redis/RedisRefreshTokenStore.java`
- `global/exception/GlobalExceptionHandler.java`

### 6.3 주요 authorization guard

- service ADMIN: `AdminAccessPolicy`, `AdminUserManagementService`
- campus role: `CampusAccessPolicy`, `CampusRolePolicy`, 각 campus use-case service
- billing/account owner: `BillingAccessPolicy`, `PaymentAccount*Service`, `Charge*Service`
- poll/anonymous/visibility: `PollAccessService`, `PollLookupSupport`, Poll command/query services
- prayer/group: `PrayerAccessSupport`, Prayer command/query services
- devotion: Devotion command/query service의 ACTIVE membership/manager guard
- notification/FCM: `NotificationRequestCommandService`, `NotificationLogQueryService`, `FcmTokenCommandService`
- scheduler: `NotificationDeduplicationService`, `NotificationLockService`, batch job services

### 6.4 인프라와 공급망

- `Dockerfile`, `docker-compose.yml`
- `application.yml`, `application-local.yml`, `application-dev.yml`,
  `application-docker.yml`, `application-prod.example.yml`, `application-test.yml`
- `docs/deploy/cloud-run-supabase.md`
- `.env.example`, `.env.local.example`, `.env.docker.example`, `.env.prod.example`
- `.github/workflows/ci.yml`, `.github/workflows/project-docs-check.yml`
- Gradle wrapper/build files와 Firebase/Redis adapter

## 7. STRIDE 요약

| 구성요소 | S | T | R | I | D | E | 주요 방어/남은 확인 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 모바일/JWT | token 탈취 | request/ID 변조 | 사용자 행위 부인 | token/PII 노출 | login 반복 | role 위조 | 서명/type/version/blacklist; 기기 저장소·rate limit 미확인 |
| Cloud Run API | 위조 principal | DTO/상태 변조 | 관리자 변경 감사 부족 | 과다 DTO | 요청 폭주 | BOLA/BFLA | default authenticated + service guard; edge 설정 미확인 |
| Supabase | DB 계정 탈취 | tenant row 변조 | DB audit 부족 | 전체 자산 유출 | connection 고갈 | 과권한 DB role | secret/env와 scope query; RLS/IAM/backup 미확인 |
| Upstash | Redis credential 탈취 | JTI/lock 삭제 | key 변경 추적 부족 | session identifier 노출 | Redis 장애 | 다른 key 접근 | TLS/password/TTL/fail-closed; ACL/alert 미확인 |
| Firebase FCM | 서비스 계정 위조 | payload/token 변조 | delivery 분쟁 | token/log 노출 | provider 제한 | 다른 사용자 발송 | owner transfer/stale filter/admin scope; IAM 미확인 |
| Scheduler | 중복 인스턴스 | 잘못된 자동 정산/삭제 | job 행위 부인 | log 예외 노출 | lock 실패 | 내부 use-case 오용 | lock/dedup/transaction/error log; runtime enable 미확인 |
| CI/Image | action 공급망 | artifact 변조 | provenance 부족 | secret log | build 중단 | workflow 권한 확대 | contents:read/wrapper validation; SHA pin/서명 미확인 |

## 8. 정적 검사 결과 요약

- 현재 tree와 좁은 git history secret prefix 검색에서 실제 key 형식 후보 없음.
- 추적 중인 env 파일은 placeholder 전용 example 4개뿐이며 실제 `.env*`, Firebase service-account,
  secret YAML은 `.gitignore` 대상이다.
- 사용자 입력을 문자열로 연결한 native SQL, OS command 실행, inbound webhook, file upload,
  WebSocket, LLM 호출 surface는 발견하지 못했다.
- 외부 integration은 Supabase PostgreSQL, Upstash Redis, Firebase FCM 세 종류다.
- background scheduler entrypoint는 8개다.

이 결과는 저장소 증거에 한정된다. 이미 배포된 이미지, 삭제된 remote branch, Cloud/Firebase/
Supabase/Upstash 콘솔 값과 조직 권한은 별도 운영 체크가 필요하다.
