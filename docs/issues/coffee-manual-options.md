# #263 커피 투표 항목 직접 입력

사용자 요청일: 2026-10-16

## 범위
- 실제 커피 투표 생성 및 진행 중 사용자 항목 추가에서 content/priceAmount 직접 입력 지원.
- 카탈로그 선택, 권한, 마감, 중복 검사와 CLOSED 정산을 유지.
- DB 마이그레이션 없음. 프론트와 반복 템플릿 제외.

## 검증
- CoffeeManualOptionTest: 정상 입력, 공백·길이, 누락·0·음수, 필드 혼합 검증.
- PollServiceTest: 생성, 사용자 추가, 중복, 응답 후 종료 정산과 멱등성.
- PollApiRestDocsTest: JSON 매핑 및 문서화.

GitHub Issue: https://github.com/FaithLog/FaithLog-backend/issues/263
GitHub 확인 결과 연결된 Projects 카드가 없습니다. 결과는 개발 로그 참조.
