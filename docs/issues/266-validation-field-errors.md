# #266 입력 검증 오류에 필드·제약 코드 제공

사용자는 입력값 오류를 입력칸에서 구체적으로 확인할 수 있도록 수정을 요청했다. HTTP 400과 GLOBAL_VALIDATION_FAILED, 기존 message는 유지하며 data.fieldErrors에 field와 code만 추가한다. 기존 오류 응답을 읽는 구형 클라이언트와 호환된다. rejectedValue, 검증 인자나 입력값은 추가하지 않는다. DB, 업무 검증 기준과 인가는 변경하지 않는다.

ValidationErrorResponseTest는 실제 Bean Validation과 MockMvc 응답으로 여러 오류, 결정적인 순서, 입력값 비노출, 잘못된 JSON의 기존 응답을 검증하고 validation-field-errors REST Docs snippets를 생성한다. 공통 index와 기존 인증 오류 문서의 선택적 data 설명을 갱신했다.

환경: 저장소 Gradle wrapper의 직접 다운로드는 네트워크 제약으로 실행되지 않았다. 로컬 임시 JDK 21과 Gradle 8.14.5를 준비하여 집중 테스트 2개 통과를 확인했다. 전체 검증은 별도로 기록한다. Mockito 동적 에이전트 부착이 제한되어 테스트 시작 시 Byte Buddy 에이전트를 지정하며, 프로젝트 런타임 설정이나 의존성은 수정하지 않는다.

GitHub Projects 도구와 기존 Obsidian 로컬 Vault 경로는 이 실행 환경에서 이용할 수 없어 저장소에 작업 기록을 남긴다. TDD 테스트는 구현 전에 추가했으나 최초 RED 실행은 도구 다운로드 제약으로 완료하지 못했다. 이후 실제 HTTP 응답 검증은 집중 테스트로 완료했다.

## 최종 검증 및 리뷰

- Java 21 / Gradle 8.14.5, 임시 테스트 에이전트와 격리된 캐시를 사용한 `test asciidoctor`: BUILD SUCCESSFUL (8분 10초).
- 209개 테스트 스위트, 1017개 테스트, 실패 0 / 오류 0 / 건너뜀 24.
- REST Docs field-errors 응답 snippets와 전체 HTML API 문서 생성 확인.
- 전체 diff 리뷰: 기존 status/code/message 보존, 검증 상세만 선택적으로 추가, DB/Flyway/의존성/권한 변경 없음, 입력값 비노출, 기존 클라이언트 호환 확인. 런타임 모킹 에이전트와 임시 JDK/캐시 설정은 저장소에 포함하지 않는다.
- Docker QA, 실기기 QA, Notion 및 Obsidian 외부 기록, GitHub Projects 상태 변경은 미실행. 검토용 브랜치 단계이며 통합/운영 배포는 수행하지 않는다.
