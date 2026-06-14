# Decision Log

프로젝트 주요 의사결정을 기록합니다. 결정은 나중에 변경될 수 있지만, 변경 이유와 시점을 남깁니다.

| Date | Decision | Context | Options | Chosen | Consequence |
| --- | --- | --- | --- | --- | --- |
| TBD | MSA 대신 모듈러 모놀리스 선택 | 초기 MVP는 작은 팀과 빠른 개발이 중요함 | MSA, 계층형 모놀리스, 모듈러 모놀리스 | 모듈러 모놀리스 | 하나의 Spring Boot 앱 안에서 도메인 경계를 유지 |
| TBD | PostgreSQL은 자동 설정 사용 | 별도 Config 과잉 방지 | PostgreSqlConfig, application.yml 자동 설정 | application.yml | 설정 단순화 |
| TBD | RedisConfig만 global에 배치 | Redis 사용처는 도메인별로 다름 | global repository, domain infra redis | domain infra redis | Redis 구현체가 도메인 책임에 남음 |
| TBD | PollComment와 PollResponse 분리 | 댓글과 응답 생명주기가 다름 | 동일 Entity, 분리 Entity | 분리 Entity | 투표 응답 집계와 댓글 기능 결합도 감소 |
| TBD | Billing은 계좌 스냅샷 저장 | 계좌 변경 시 기존 청구 데이터 보존 필요 | 계좌 직접 참조, 스냅샷 | 스냅샷 | 오래된 청구의 입금 계좌 정보 보존 |
