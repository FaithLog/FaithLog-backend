# Workflow Rules

## Issue Creation

- GitHub Issue를 생성하고 `[Type] 작업 내용` 형식으로 제목을 작성합니다.
- 라벨은 `domain:*`, `type:*`, `priority:*`, `layer:*`, `needs:*`를 조합합니다.
- 이슈에는 Context, User Story, Acceptance Criteria, Test Notes, Docker Check, Secret Check를 포함합니다.

## Kanban Movement

- To Do -> In Progress: 최신 `develop` pull, 브랜치 생성, 담당자 배정.
- In Progress -> Code Review: 작업 커밋, Docker 확인, PR 생성.
- Code Review -> Done: 리뷰 반영, CI 통과, PR 머지, DoD 충족.

## Development Flow

```bash
git switch develop
git pull origin develop
git switch -c feat/14-sign-up-api
```

작업 후:

```bash
git add .
git commit -m "feat: #14 회원가입 API 구현"
git push origin feat/14-sign-up-api
```

브랜치가 push되면 `.github/workflows/auto-draft-pr.yml`이 `develop` 대상 Draft PR을 자동 생성합니다.

## Code Review Checklist

- [ ] 오류 없는 코드만 PR로 올라왔다.
- [ ] Docker 환경에서 동작한다.
- [ ] Secret Key와 `.env`가 커밋되지 않았다.
- [ ] DDD 패키지 경계를 지켰다.
- [ ] DTO/Command/Result 규칙을 지켰다.
- [ ] 테스트 또는 테스트 시나리오가 있다.

## Blocked Rule

Blocked 이슈는 다음을 반드시 작성합니다.

- blocker reason
- blocked by
- unblock action
- owner
- next check date

## Hotfix Rule

- 운영 장애성 수정만 `hotfix/*`를 허용할 수 있습니다.
- MVP 전에는 가능하면 `fix/<issue>-description`으로 처리합니다.
- Hotfix도 Secret 노출 금지와 Docker 확인 원칙을 지킵니다.

## Release Rule

- 릴리즈 이슈는 `[Release] MVP 버전 릴리즈` 형식으로 생성합니다.
- release 브랜치는 `release/30-mvp-release` 형식을 사용합니다.
- 릴리즈 전 Docker, Secret, Swagger, 문서, 포트폴리오 로그를 확인합니다.

## Documentation Rule

- API 변경은 Swagger 또는 API 문서를 업데이트합니다.
- 아키텍처 결정은 `decision-log.md`에 기록합니다.
- 포트폴리오에 쓸 수 있는 결정은 `portfolio-log.md`에 기록합니다.
