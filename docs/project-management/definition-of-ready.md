# Definition of Ready

Ready는 바로 개발을 시작해도 되는 상태입니다. 모든 이슈는 Ready로 이동하기 전에 아래 조건을 만족해야 합니다.

## Required

- [ ] 문제 정의가 있다.
- [ ] 사용자 가치 또는 기술적 가치가 있다.
- [ ] Acceptance Criteria가 있다.
- [ ] 선행 작업이 명시되어 있다.
- [ ] 예상 산출물이 있다.
- [ ] 관련 Epic이 연결되어 있다.
- [ ] 브랜치 타입이 정해져 있다.
- [ ] PR target은 `develop`으로 정해져 있다.
- [ ] 테스트 방법이 최소 1개 이상 있다.

## API Work

- [ ] 요청/응답 초안이 있다.
- [ ] 인증 필요 여부가 정리되어 있다.
- [ ] Swagger 표시 여부가 정리되어 있다.
- [ ] 에러 응답 케이스가 있다.

## DB Work

- [ ] 테이블/필드 영향이 정리되어 있다.
- [ ] 기존 데이터와의 충돌 가능성이 검토되었다.
- [ ] 마이그레이션 또는 초기 schema 전략이 정리되어 있다.

## Security Work

- [ ] 인증/인가 범위가 정리되어 있다.
- [ ] Secret Key 또는 `.env` 노출 위험이 검토되었다.
- [ ] 관리자 API라면 권한 조건이 명확하다.

## Cross-Domain Work

- [ ] 다른 도메인과 연결되는 작업이면 dependency가 명시되어 있다.
- [ ] 다른 도메인의 Entity 직접 참조 대신 ID 참조를 우선한다.
- [ ] 연동 테스트 시나리오가 있다.

## Not Ready Examples

- Acceptance Criteria가 "잘 동작해야 함"처럼 모호하다.
- 선행 작업이 있는데 Depends on이 비어 있다.
- Docker 확인 방법이 없다.
- API 요청/응답 초안이 없다.
- `.env` 또는 Firebase key 처리 방식이 정리되지 않았다.
