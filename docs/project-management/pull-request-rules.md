# Pull Request Rules

오류가 없는 코드만 PR로 보냅니다. Docker 환경에서 동작하지 않는 코드는 머지하지 않습니다. Secret Key와 `.env` 파일은 절대 커밋하지 않습니다.

## PR Target

- 작업 브랜치 -> `develop`
- `main` 직접 PR 금지
- `develop` 직접 push 금지

## Title Format

```text
[Type] #issue-number 작업 내용
```

## Examples

```text
[Feat] #14 회원가입 API 구현
[Fix] #21 로그인 실패 에러 메시지 수정
[Docs] #5 README 실행 방법 작성
```

## Body Template

```markdown
## 연결 이슈
- Closes #

## 작업 내용
-

## 변경 도메인
- [ ] user
- [ ] campus
- [ ] devotion
- [ ] billing
- [ ] poll
- [ ] notification
- [ ] global

## 아키텍처 규칙 확인
- [ ] Controller에서 Entity를 직접 반환하지 않았습니다.
- [ ] Request DTO와 Command를 분리했습니다.
- [ ] Response DTO를 사용했습니다.
- [ ] 도메인별 패키지 경계를 지켰습니다.
- [ ] 다른 도메인의 Entity 직접 참조를 피했습니다.

## 실행 확인
- [ ] 로컬에서 정상 동작합니다.
- [ ] Docker 환경에서 정상 동작합니다.
- [ ] Swagger에서 API를 확인했습니다.

## 보안 확인
- [ ] .env 파일을 커밋하지 않았습니다.
- [ ] Secret Key가 노출되지 않았습니다.
- [ ] 인증/인가가 필요한 API에 보안 설정을 적용했습니다.

## 테스트
- [ ] 테스트 코드를 작성했습니다.
- [ ] 테스트 시나리오를 작성했습니다.
- [ ] 수동 테스트를 완료했습니다.

## 리뷰 포인트
-

## 포트폴리오 기록
-
```

## Review Focus

- DDD 패키지 경계가 유지되었는가
- Request DTO가 Service로 직접 들어가지 않는가
- Entity를 Controller에서 반환하지 않는가
- Redis Key/TTL/실패 동작이 정의되었는가
- Docker에서 실행 가능한가
- Secret이 노출되지 않았는가
