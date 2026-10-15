# #263 커피 투표 직접 입력 항목

## 요청 및 구현
- 사용자 요청에 따라 백엔드를 먼저 수정. 프론트와 반복 템플릿은 제외.
- 기준: 원격 main f63bd081 (원격 ls-remote 일치 확인), codex/coffee-manual-options.
- 직접 투표 생성: options에서 menuId 또는 content/priceAmount 사용.
- 사용자 항목 추가: POST /api/v1/campuses/{campusId}/polls/{pollId}/options에 content/priceAmount 지원.
- 이름 trim 후 1~200자, 가격 1~2147483647의 정수. 소수·문자열·범위 초과 금액은 JSON 파싱에서 거부.
- 기존 카탈로그 선택 시 서버 snapshot 유지. 사용자 추가 API의 메뉴/직접 입력 혼합은 거부.
- 메뉴 코드 null로 기존 poll_options에 저장. DB/Flyway/의존성 변경 없음.
- 기존 회원·OPEN·allowUserOptionAdd·중복 검사, CLOSED 정산 트랜잭션과 청구 멱등성 유지.

## TDD 및 검증
- RED: 기존 코드로 새 테스트 컴파일 시 6인자 AddPollOptionCommand가 없어 실패.
- 초기 offline 실행은 Spring Boot 플러그인 캐시 해석 실패; online 실행으로 의존성 해결.
- GREEN: CoffeeManualOptionTest, PollServiceTest, PollApiRestDocsTest 선별 실행 성공(2m44s).
- 이후 금액 JSON 파싱 테스트와 직접 생성 REST Docs 예제를 추가함.
- 전체 test/asciidoctor/bootJar 실행 중 기존 BillingControllerTest 실패 발견. 수정 전 main에서도 재현 후 전체 실행 중단(exit 130). 전체 통과로 보고하지 않음.
- 최종 소스로 관련 4개 테스트 클래스 81개 모두 통과, asciidoctor/bootJar BUILD SUCCESSFUL(3m37s).
- 명령: `./gradlew test --tests '*CoffeeManualOptionTest' --tests '*PollPriceAmountRequestTest' --tests '*PollServiceTest' --tests '*PollApiRestDocsTest' asciidoctor bootJar --no-daemon`
- 테스트 실행 디렉터리: /private/tmp/faithlog-coffee-manual. 실제 운영 DB/Docker QA는 미실행.

## 기록 및 이관
- Issue: https://github.com/FaithLog/FaithLog-backend/issues/263
- 연결된 Projects 카드는 없음.
- Notion 기획서 조회 미실행; 현재 사용자 요청을 최신 결정으로 적용.
- Docker/실운영 DB QA, 배포 미실행.
- PM 세션 메시징, push, PR, merge 미실행.

## 리뷰 확인
- API 요청 DTO → Command → 사용자 항목 resolver → PollOption 저장까지 priceAmount 전달 확인.
- 메뉴 코드 null을 처리하는 결과/응답 DTO 및 기존 정산 확인.
- 직접 생성의 기존 카탈로그 필드 덮어쓰기 호환 유지. 사용자 추가의 혼합 필드 거부 유지.
- DTO 금액 파싱에서 반올림/절삭 없이 소수·문자열·int 범위 초과를 거부.
- 신규 패키지는 외부 의존성 없이 기존 Jackson API 사용.
- 변경 소스/문서 diff 공백 검사 통과.

## 기존 실패 재현
- 전체 검사 중 BillingControllerTest.charge_query_api_maps_my_summary_admin_campus_and_admin_member_responses:764 실패 발견.
- 수정 없는 원격 main f63bd081을 /private/tmp/faithlog-coffee-baseline에 별도 체크아웃한 뒤 동일 테스트 단독 실행.
- 동일 위치 실패 재현(1 test completed, 1 failed; 2m17s). 기대 summary.totalAmount=5000, 실제=2500. 이번 커피 항목 변경과 무관한 baseline 실패.

## 인계
- 영구 작업 디렉터리: /Users/josephuk77/FaithLog-worktrees/coffee-manual-options
- 브랜치: codex/coffee-manual-options. 기존 /Users/josephuk77/FaithLog 체크아웃 변경 없음.
- 변경 파일 목록과 전체 diff는 해당 브랜치에서 `git diff f63bd081...HEAD`로 확인.
- 서버 코드 구현 및 로컬 검증 완료. push/PR/merge/배포 및 이슈 종료 미실행.
