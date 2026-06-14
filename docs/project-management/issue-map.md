# Issue Map

## Issue Format

```text
[ISSUE-ID] 제목

Type:
Priority:
Estimate:
Domain:
Labels:
Status:
Depends on:
Blocks:
Related Epics:
Branch Name:
Commit Example:
PR Target:

Context:
User Story:
Task:
Acceptance Criteria:
Technical Notes:
Test Notes:
API Notes:
Data Notes:
Security Notes:
Docker Check:
Secret Check:
Observability Notes:
Definition of Ready:
Definition of Done:
Portfolio Evidence:
```

## Label Rules

예시:

```text
domain:user
type:feat
priority:p1
layer:application
needs:test
portfolio:evidence
```

## Sample Issue

```text
[AUTH-004] Refresh Token Redis 저장

Type: Feat
Priority: P0
Estimate: M
Domain: user
Labels: domain:user, type:feat, priority:p0, layer:infrastructure, needs:test, redis
Status: Backlog
Depends on: SETUP-004, AUTH-003
Blocks: AUTH-006
Related Epics: EPIC-01
Branch Name: feat/{github-issue-number}-refresh-token-redis
Commit Example: feat: #{github-issue-number} Refresh Token Redis 저장 구현
PR Target: develop

Context: 로그인 유지와 토큰 재발급을 위해 Refresh Token을 Redis에 TTL 기반으로 저장한다.
User Story: 사용자는 Access Token이 만료되어도 Refresh Token으로 다시 로그인 상태를 유지할 수 있다.
Task: Redis key/TTL 설계, 저장/조회/삭제 repository 구현, 재발급 연동.
Acceptance Criteria: 로그인 시 Refresh Token 저장, 재발급 시 검증, 로그아웃 시 삭제.
Technical Notes: 실제 Redis 구현체는 user/infrastructure/redis에 둔다.
Test Notes: TTL, 없는 토큰, 만료 토큰, 로그아웃 후 재발급 실패 시나리오.
API Notes: /api/v1/auth/reissue
Data Notes: PostgreSQL 저장 없음.
Security Notes: Refresh Token 원문 저장 정책 검토.
Docker Check: Redis 컨테이너에서 동작 확인.
Secret Check: JWT secret 미노출.
Observability Notes: 재발급 실패 로그 레벨 정의.
Definition of Ready: Redis 설정 완료, 로그인 API 완료.
Definition of Done: DoD 충족, Docker 확인, 테스트 시나리오 기록.
Portfolio Evidence: Redis TTL 기반 인증 상태 관리 설계 기록.
```

## Issue Registry

전체 이슈 목록은 `backlog.md`를 기준으로 관리합니다. GitHub Issue 생성 시 이 문서의 형식을 사용하고, 실제 GitHub Issue 번호를 브랜치/커밋/PR에 연결합니다.

| Prefix | Domain | Epic |
| --- | --- | --- |
| SETUP | global | EPIC-00 |
| ARCH | global | EPIC-00 |
| USER | user | EPIC-01 |
| AUTH | user | EPIC-01 |
| CAMPUS | campus | EPIC-02 |
| DEVOTION | devotion | EPIC-03 |
| BILLING | billing | EPIC-04 |
| POLL | poll | EPIC-05 |
| NOTI | notification | EPIC-06 |
| ADMIN | domain-specific admin controllers | EPIC-07 |
| BATCH | scheduler | EPIC-08 |
| DEPLOY | infra/docker | EPIC-09 |
| QA | test/docs | EPIC-10 |
| DOCS | docs | EPIC-10 |
