# 로그인 사용자 비밀번호 변경

## 배경

FaithLog의 이메일 인증 기반 비밀번호 재설정은 비밀번호를 잊은 사용자를 위한 흐름이다. 로그인한 사용자가 내 정보 화면에서 비밀번호를 바꿀 때는 이메일 인증 대신 현재 비밀번호를 확인하는 별도 API가 필요하다.

## API

```http
PATCH /api/v1/users/me/password
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "currentPassword": "현재 비밀번호",
  "newPassword": "새 비밀번호"
}
```

성공하면 `data`가 `null`인 공통 성공 응답을 반환하고 새 Access/Refresh Token을 발급하지 않는다. 클라이언트는 저장된 인증 정보를 제거하고 로그인 화면으로 이동한다.

## 보안 경계

- active user row를 pessimistic lock으로 조회한다.
- 현재 비밀번호 불일치와 현재 비밀번호 재사용을 서로 다른 400 오류 코드로 구분한다.
- 새 비밀번호 hash 변경과 `tokenVersion` 증가를 같은 DB transaction에서 수행한다.
- 모든 Refresh Token session을 삭제해 다른 기기를 포함한 로그인을 만료시킨다.
- Redis 삭제가 실패하면 DB 변경도 rollback한다.
- 기존 FCM token, 프로필, role, campus membership은 변경하지 않는다.
- 비밀번호를 모르는 경우에는 기존 이메일 인증 password reset을 사용한다.

## 검증

- Controller: 성공, 현재 비밀번호 불일치, 같은 비밀번호, validation, 인증 실패
- Service: BCrypt 검증/변경, session 전체 삭제, 전용 오류
- Integration: session 삭제 실패 시 password hash와 tokenVersion rollback
- Structure: 전용 command service의 transaction과 Controller wiring
- REST Docs: 성공과 두 business error의 실제 request/response snippet

운영 iOS QA에서는 이름 수정과 함께 실제 Simulator 화면에서 변경, 강제 로그아웃, 이전 token 차단, 새 비밀번호 재로그인을 확인하고 테스트 계정은 최종적으로 원래 값으로 복구한다.
