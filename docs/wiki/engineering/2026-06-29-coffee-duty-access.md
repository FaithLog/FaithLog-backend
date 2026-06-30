# 2026-06-29 #100 커피 담당자 권한 보강

## 요약

- 이슈: #100 `[Feat] 커피 담당자 전용 권한과 내 담당 상태 조회 구현`
- 브랜치: `feat/100-coffee-duty-access`
- 로그인 응답과 `GET /api/v1/users/me`가 ACTIVE campus memberships를 포함하도록 수정했다.
- ACTIVE `COFFEE` 담당자 USER가 COFFEE 범위 계좌/투표/청구 조회를 사용할 수 있게 하고, 커피 외 관리자 기능은 기존 권한으로 제한했다.

## 구현

- `GET /api/v1/campuses/{campusId}/duty-assignments/me`
  - ACTIVE 멤버만 조회 가능
  - COFFEE 담당자는 `isActive=true`
  - 일반 ACTIVE 멤버는 `200 OK + isActive=false`
- COFFEE 담당자 권한
  - `accountType=COFFEE` 계좌 등록/비활성화 허용
  - `pollType=COFFEE` 투표 생성/마감/미응답자 조회 허용
  - 기본 COFFEE 템플릿과 직접 COFFEE 투표의 `allowUserOptionAdd` 생략 기본값 true
  - admin charge 조회는 `paymentCategory=COFFEE`로 제한된 경우만 허용
- 제한 유지
  - `PENALTY` 계좌/청구 관리
  - 캠퍼스 멤버 관리
  - 경건생활 관리자 기능
  - 서비스 ADMIN 기능
  - COFFEE 외 투표 타입 생성/관리

## 주의

- #97 사용자 옵션 추가 API는 `{ "content": "새 항목" }`만 받으므로 사용자 추가 옵션은 `composeMenuCode=null`, `priceAmount=0`으로 저장된다.
- 커피 투표에서 사용자 추가 옵션을 실주문/정산에 안전하게 쓰려면 메뉴 카탈로그 기반 옵션 추가 API 또는 스키마 결정이 별도로 필요하다.

## 검증

- RED: 구현 전 `./gradlew test --tests com.faithlog.user.application.AuthServiceTest --tests com.faithlog.user.presentation.UserMeControllerTest --tests com.faithlog.campus.application.CampusServiceTest --tests com.faithlog.campus.presentation.CampusControllerTest --tests com.faithlog.billing.application.BillingServiceTest --tests com.faithlog.billing.application.BillingQueryServiceTest --tests com.faithlog.poll.application.PollServiceTest`가 `CampusService.getMyCoffeeDutyAssignment` 부재로 `compileTestJava` 실패.
- Focused service/controller: 동일 테스트 묶음 성공.
- REST Docs: `./gradlew test --tests com.faithlog.campus.presentation.CampusApiRestDocsTest --tests com.faithlog.billing.presentation.BillingApiRestDocsTest --tests com.faithlog.poll.presentation.PollApiRestDocsTest` 성공.
- 추가 focused 검증: `./gradlew test --tests com.faithlog.poll.application.PollServiceTest --tests com.faithlog.poll.presentation.PollApiRestDocsTest --tests com.faithlog.billing.application.BillingServiceTest --tests com.faithlog.billing.application.BillingQueryServiceTest --tests com.faithlog.campus.presentation.CampusApiRestDocsTest` 성공.
- 전체 검증: `./gradlew test` 성공(256 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build` 성공, `./gradlew asciidoctor` 성공, `git diff --check` 성공.
- 정적 확인: Swagger 문서화 annotation 추가 0건.
- Docker/API QA:
  - 기본 compose는 기존 local named volume credential mismatch로 app이 `FATAL: password authentication failed for user "faithlog"`로 부팅 실패했다. 기존 볼륨은 삭제하지 않았다.
  - 별도 compose override/project `faithlog-qa100`로 격리 스택을 올려 `GET /api/v1/health` `UP` 확인.
  - 실제 HTTP API로 회원가입, ACTIVE 멤버 가입, COFFEE 담당 지정, 로그인/users-me 멤버십, duty me true/false, COFFEE 계좌 등록/비활성화, PENALTY 계좌 403, 다른 캠퍼스 담당자 403, COFFEE 투표 생성, CUSTOM 투표 403, 결과/미응답자/COFFEE 청구 조회 200, PENALTY 청구/멤버관리/대시보드/서비스 ADMIN 403을 검증했다.
  - QA 스택은 `docker compose ... down`으로 정리했다.
- PM 리뷰 보강:
  - 기본 COFFEE 템플릿 `allowUserOptionAdd=true`.
  - 기본 COFFEE 템플릿 기반 생성 poll도 `allowUserOptionAdd=true`.
  - 직접 COFFEE poll은 requester가 COFFEE 담당자인지와 무관하게 omission/null 기본 true, 명시 false는 false 유지.
  - CUSTOM 등 커피 외 direct poll omission은 기존 false 유지.
  - RED: 보강 전 `PollServiceTest` 3건 실패.
  - GREEN: `./gradlew test --tests com.faithlog.poll.application.PollServiceTest`, `./gradlew test --tests com.faithlog.poll.presentation.PollApiRestDocsTest`, `./gradlew test`(258 tests / 0 failures / 0 errors / 1 skipped), `./gradlew build`, `./gradlew asciidoctor` 성공.
