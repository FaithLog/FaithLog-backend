# Branch Strategy

## Format

```text
type/issue-number-description
```

## Allowed Types

- `feat`
- `fix`
- `build`
- `chore`
- `docs`
- `style`
- `refactor`
- `test`
- `release`

## Examples

```text
feat/1-swagger-api-docs
feat/14-sign-up-api
fix/21-login-error-message
build/3-gradle-dependency
chore/4-gitignore-config
docs/5-readme-run-guide
style/6-code-formatting
refactor/7-sign-up-service
test/8-login-test
release/30-mvp-release
```

## Before Creating a Branch

- [ ] `develop` 브랜치로 이동했다.
- [ ] `git pull origin develop`을 실행했다.
- [ ] 관련 이슈가 생성되어 있다.
- [ ] 이슈 번호를 확인했다.
- [ ] 브랜치 이름이 규칙을 만족한다.

## Commands

```bash
git switch develop
git pull origin develop
git switch -c feat/14-sign-up-api
```

## Forbidden

- `main`에서 직접 작업 브랜치 생성
- `develop`에 직접 push
- 이슈 번호 없는 브랜치
- `ffeat/...`처럼 오타가 있는 브랜치 타입
