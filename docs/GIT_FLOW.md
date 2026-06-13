# FaithLog Backend Git Flow

FaithLog 백엔드는 `main`과 `develop`을 기준으로 개발합니다.

## Branches

- `main`: 제품으로 출시될 수 있는 안정 브랜치입니다.
- `develop`: 다음 출시 버전을 개발하는 브랜치입니다. 오류가 없는 코드만 머지합니다.
- 작업 브랜치: 항상 최신 `develop`에서 생성합니다.

## Development Process

1. GitHub Issue를 생성합니다.
2. 최신 `develop`을 받습니다.
3. 이슈 번호를 포함한 작업 브랜치를 생성합니다.
4. 개발하면서 작업 단위로 커밋합니다.
5. 자신의 브랜치에서 `develop`으로 Pull Request를 생성합니다.
6. 코드 리뷰를 받은 뒤 머지합니다.

## Branch Naming

브랜치 이름은 다음 형식을 사용합니다.

```text
<type>/<issue-number>-<short-description>
```

예시:

```text
feat/1-swagger-api-docs
fix/14-login-error-message
chore/22-update-gitignore
```

허용되는 type:

```text
feat
fix
build
chore
docs
style
refactor
test
release
```

## Issue Titles

이슈 제목은 다음 형식을 권장합니다.

```text
[Feat] 회원가입 기능 구현
[Fix] 로그인 실패 시 에러 메시지 미출력 문제 수정
[Build] Gradle 의존성 설정 수정
[Chore] .gitignore 설정 추가
[Docs] README 프로젝트 실행 방법 작성
[Style] 코드 포맷팅 적용
[Refactor] 회원가입 서비스 로직 분리
[Test] 로그인 기능 테스트 코드 추가
[Release] MVP 버전 릴리즈
```

## Commit Message

커밋 메시지는 이슈 번호를 포함합니다.

```text
<type>: #<issue-number> <message>
```

예시:

```text
feat: #14 회원가입 API 구현
fix: #15 로그인 실패 응답 메시지 수정
chore: #16 gitignore 개발툴 파일 제외 추가
```

허용되는 type:

- `feat`: 새로운 기능 추가, 기존 기능을 요구사항에 맞게 수정
- `fix`: 기능 버그 수정
- `build`: 빌드 관련 수정
- `chore`: 패키지 매니저 수정, 기타 설정 수정
- `docs`: 문서 수정
- `style`: 코드 포맷팅, 세미콜론, import 정리 등
- `refactor`: 기능 변화 없는 코드 리팩터링
- `test`: 테스트 코드 추가/수정
- `release`: 버전 릴리즈

## Pull Request

- PR base branch는 `develop`입니다.
- `main`으로 직접 PR을 보내지 않습니다. 릴리즈 시에만 `develop`에서 `main`으로 머지합니다.
- PR 전 Docker 환경에서 실행되는지 확인합니다.

## Before Commit

커밋 전 반드시 확인합니다.

- 내가 구현한 서비스가 로컬뿐 아니라 Docker에서도 동작하는가
- Docker에서 동작하지 않는 코드를 올리고 있지 않은가
- `.env` 파일, JWT secret, Firebase key, 인증서 등이 커밋에 포함되지 않았는가
- 커밋 메시지에 이슈 번호가 포함되어 있는가

## Local Git Hooks

이 저장소는 Python 기반 Git hook을 제공합니다.

한 번만 실행하면 됩니다.

```bash
git config core.hooksPath .githooks
```

활성화되는 훅:

- `.githooks/pre-commit`: 브랜치 이름과 커밋 대상 파일을 검사합니다.
- `.githooks/commit-msg`: 커밋 메시지 형식을 검사합니다.

훅은 다음을 막습니다.

- `main`, `develop`에서 직접 커밋
- 규칙에 맞지 않는 브랜치 이름
- `.env`, key, 인증서, IDE 설정, OS 임시 파일, 빌드 산출물, 로컬 설계 문서 커밋
- 이슈 번호 없는 커밋 메시지
