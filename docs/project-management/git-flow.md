# Git-Flow

FaithLog는 `main`, `develop`, 작업 브랜치를 사용하는 Git-Flow 기반 협업 프로세스를 따릅니다.

## Development Order

```text
이슈 생성
-> 브랜치 생성
-> 개발
-> 작업 단위 커밋
-> Pull Request 생성
-> develop 브랜치로 PR 요청
-> 코드 리뷰
-> 오류 없는 코드만 머지
```

## Branch Roles

- `main`: 제품으로 출시될 수 있는 안정 브랜치입니다.
- `develop`: 다음 출시 버전을 개발하는 통합 브랜치입니다.
- 작업 브랜치: 이슈 단위로 생성하는 구현 브랜치입니다.

## Issue Rule

- 모든 기능 개발은 이슈 생성 후 시작합니다.
- 이슈 제목은 `[Type] 작업 내용` 형식을 사용합니다.
- 이슈에는 Acceptance Criteria, Depends on, 예상 산출물, 테스트 방법을 포함합니다.

## Branch Rule

- 작업 브랜치는 항상 최신 `develop`에서 생성합니다.
- 브랜치를 만들기 전에 반드시 `develop`을 pull 받습니다.
- 브랜치 이름은 `type/issue-number-description` 형식입니다.
- 사용자가 예시로 적은 `ffeat/1-swagger-api-docs`는 오타이며, 실제 규칙은 `feat/1-swagger-api-docs`입니다.

## PR Rule

- PR은 반드시 작업 브랜치에서 `develop` 브랜치로 보냅니다.
- `main`으로 직접 PR을 보내지 않습니다.
- `develop`에도 직접 push하지 않습니다.
- 오류가 없는 코드만 PR로 보냅니다.

## Review Rule

- 리뷰어는 DDD 패키지 경계, DTO/Command/Result 흐름, Entity 반환 금지, Redis Key/TTL, 예외 처리를 확인합니다.
- 리뷰 코멘트가 해결되기 전에는 QA / Test로 이동하지 않습니다.

## Merge Rule

- 오류 없는 코드만 머지합니다.
- Docker 환경에서 동작하지 않는 코드는 머지하지 않습니다.
- Secret Key와 `.env` 파일은 절대 커밋하지 않습니다.
- DoD를 만족하지 않으면 Done으로 이동하지 않습니다.
