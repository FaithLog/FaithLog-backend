# Definition of Done

Done은 구현, 리뷰, 테스트, 문서, 운영 안전성 확인까지 끝난 상태입니다.

## Product and API

- [ ] Acceptance Criteria를 충족했다.
- [ ] Swagger에서 API 확인 가능하다.
- [ ] 테스트 코드 또는 테스트 시나리오가 존재한다.
- [ ] README 또는 API 문서가 업데이트되었다.
- [ ] 로그/에러 처리 동작을 확인했다.

## Architecture

- [ ] Controller에서 Entity를 직접 반환하지 않는다.
- [ ] Request DTO와 Command를 분리했다.
- [ ] Response DTO를 사용했다.
- [ ] 필요한 경우 Result를 사용했다.
- [ ] 도메인별 패키지 경계를 준수했다.
- [ ] 주요 비즈니스 규칙은 Entity 또는 Domain Service에 위치한다.
- [ ] Repository 인터페이스와 JPA 구현체 역할을 분리했다.
- [ ] 예외 코드가 정의되어 있다.

## Security

- [ ] 보안이 필요한 API는 인증을 적용했다.
- [ ] 관리자 API는 권한 검증을 적용했다.
- [ ] `.env`/Secret 노출이 없다.
- [ ] JWT Secret, Firebase Key, DB Password가 커밋되지 않았다.

## Redis and Cross-Domain

- [ ] Redis를 사용하는 경우 TTL, Key 규칙, 실패 시 동작을 정의했다.
- [ ] 다른 도메인과 연결되는 기능은 연동 테스트 시나리오를 작성했다.
- [ ] 다른 도메인의 Entity 직접 참조를 피했다.

## Workflow

- [ ] 로컬 실행을 확인했다.
- [ ] Docker 실행을 확인했다.
- [ ] 커밋 메시지에 이슈 번호를 연결했다.
- [ ] `develop`으로 PR을 생성했다.
- [ ] 코드 리뷰가 완료되었다.
- [ ] 오류 없는 코드만 Done 처리했다.
- [ ] 포트폴리오 증거가 필요한 작업은 `portfolio-log.md`에 기록했다.
