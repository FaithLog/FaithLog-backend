# Kanban Board

FaithLog 백엔드 칸반 보드는 실제 GitHub Issue만 카드로 관리합니다. Draft card는 사용하지 않고, 카드 하나는 반드시 하나의 GitHub Issue와 연결합니다.

## Columns

| Column | WIP Limit | Purpose |
| --- | ---: | --- |
| To Do | 제한 없음 | 아직 개발을 시작하지 않은 기능/작업 이슈 |
| In Progress | 2 | 브랜치를 만들고 구현 중인 이슈 |
| Code Review | 2 | `develop` 대상 PR이 열려 리뷰 중인 이슈 |
| Done | 제한 없음 | PR 머지와 Definition of Done을 만족한 이슈 |

## To Do

- 컬럼 목적: 구현 대기 중인 이슈를 개발 순서대로 관리합니다.
- 들어올 수 있는 작업 유형: 기능, 빌드, 문서, 리팩터링, 테스트, 릴리즈.
- 다음 컬럼 이동 조건: 최신 `develop` pull, 이슈 번호 기반 브랜치 생성, 담당자 배정.
- 이동 금지 조건: 작업 범위가 모호하거나 선행 작업이 끝나지 않았습니다.
- Git 브랜치 상태: 아직 없거나 생성 직전.
- PR 상태: 없음.
- 산출물: 준비된 GitHub Issue.

## In Progress

- 컬럼 목적: 실제 구현 중인 이슈를 제한된 수만 유지합니다.
- 들어올 수 있는 작업 유형: 브랜치가 생성된 작업.
- 다음 컬럼 이동 조건: 작업 단위 커밋 완료, 로컬 테스트, Docker 확인, Secret 확인, PR 생성.
- 이동 금지 조건: Docker에서 동작하지 않거나 `.env`/Secret 노출 위험이 있습니다.
- Git 브랜치 상태: `type/issue-number-description`.
- PR 상태: 없음 또는 Draft.
- 산출물: 구현 코드, 테스트, 문서 업데이트.

## Code Review

- 컬럼 목적: PR 리뷰와 아키텍처 규칙 검증을 수행합니다.
- 들어올 수 있는 작업 유형: `develop` 대상 PR.
- 다음 컬럼 이동 조건: 리뷰 코멘트 반영, CI 통과, DDD 경계 준수, 테스트/문서 확인.
- 이동 금지 조건: 리뷰 코멘트가 미반영이거나 오류 있는 코드입니다.
- Git 브랜치 상태: 원격 작업 브랜치 push 완료.
- PR 상태: Open 또는 Ready for review.
- 산출물: PR, 리뷰 코멘트, 수정 커밋.

## Done

- 컬럼 목적: 완료된 작업과 포트폴리오 증거를 보관합니다.
- 들어올 수 있는 작업 유형: DoD를 충족한 머지 완료 작업.
- 다음 컬럼 이동 조건: 없음.
- 이동 금지 조건: DoD 미충족.
- Git 브랜치 상태: 머지 후 삭제 가능.
- PR 상태: Merged.
- 산출물: 머지된 PR, 테스트 증거, 문서, 포트폴리오 로그.

## Movement Rules

### To Do -> In Progress

- 최신 `develop` 브랜치를 pull 받았다.
- 이슈 번호를 기준으로 작업 브랜치를 생성했다.
- 브랜치 이름이 규칙을 만족한다. 예: `feat/27-signup-login-jwt`.
- Project Board에서 이슈를 `In Progress`로 이동했다.

### In Progress -> Code Review

- 작업 단위 커밋이 완료되었다.
- 커밋 메시지에 이슈 번호가 연결되어 있다.
- 로컬에서 실행 확인했다.
- Docker 환경에서 실행 확인했다.
- `.env`/Secret 노출이 없는지 확인했다.
- `develop` 브랜치로 PR을 생성했다.
- PR 템플릿을 작성했다.

### Code Review -> Done

- 리뷰어가 주요 변경사항을 확인했다.
- 리뷰 코멘트를 반영했다.
- CI가 통과했다.
- Docker 환경에서 정상 동작한다.
- Definition of Done을 만족한다.
- PR이 `develop`에 머지되었다.

## Branch Push Automation

규칙에 맞는 브랜치를 push하면 `.github/workflows/auto-draft-pr.yml`이 `develop` 대상 Draft PR을 자동 생성합니다.

예:

```bash
git switch develop
git pull origin develop
git switch -c feat/27-signup-login-jwt
git push origin feat/27-signup-login-jwt
```
