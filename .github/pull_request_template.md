오류가 없는 코드만 PR로 보냅니다.
Docker 환경에서 동작하지 않는 코드는 머지하지 않습니다.
Secret Key와 .env 파일은 절대 커밋하지 않습니다.

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

## Git-Flow 확인

- [ ] PR target branch가 `develop`입니다.
- [ ] 브랜치 이름이 `<type>/<issue-number>-<description>` 형식입니다.
- [ ] 커밋 메시지가 `type: #issue-number message` 형식입니다.

## 아키텍처 규칙 확인

- [ ] Controller에서 Entity를 직접 반환하지 않았습니다.
- [ ] Request DTO와 Command를 분리했습니다.
- [ ] Response DTO를 사용했습니다.
- [ ] 필요한 경우 Result를 사용했습니다.
- [ ] 도메인별 패키지 경계를 지켰습니다.
- [ ] 다른 도메인의 Entity 직접 참조를 피했습니다.
- [ ] PostgreSQL은 Spring Boot 자동 설정을 사용합니다.
- [ ] Redis 구현체는 사용하는 도메인의 `infrastructure/redis` 아래에 있습니다.

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
