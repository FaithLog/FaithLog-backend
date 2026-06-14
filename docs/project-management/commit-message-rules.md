# Commit Message Rules

## Format

```text
type: #issue-number 커밋 메시지
```

## Examples

```text
feat: #14 회원가입 API 구현
fix: #21 로그인 실패 에러 메시지 수정
build: #3 Gradle 의존성 설정 수정
chore: #4 .gitignore 설정 추가
docs: #5 README 실행 방법 작성
style: #6 코드 포맷팅 적용
refactor: #7 회원가입 서비스 로직 분리
test: #8 로그인 테스트 코드 추가
release: #30 MVP 버전 릴리즈
```

## Types

- `feat`: 새로운 기능 추가, 기존 기능을 요구사항에 맞게 수정
- `fix`: 기능에 대한 버그 수정
- `build`: 빌드 관련 수정
- `chore`: 패키지 매니저 수정, 기타 설정 수정
- `docs`: 문서 또는 주석 수정
- `style`: 코드 스타일, 포맷팅 수정
- `refactor`: 기능 변화 없는 코드 리팩터링
- `test`: 테스트 코드 추가/수정
- `release`: 버전 릴리즈

## Before Commit

- [ ] 로컬 실행 확인
- [ ] Docker 실행 확인
- [ ] `.env` 파일 미포함
- [ ] Secret Key 미노출
- [ ] 이슈 번호 포함
- [ ] 불필요한 로그 제거
- [ ] 임시 코드 제거
- [ ] Swagger 또는 API 테스트 방법 정리

## Bad Examples

```text
회원가입 구현
feat: 회원가입 구현
feat: # 회원가입 구현
feat #14 회원가입 구현
```
