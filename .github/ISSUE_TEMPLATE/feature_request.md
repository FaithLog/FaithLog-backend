---
name: Feature Request
about: 기능 개발 이슈
title: "[Feat] "
labels: "feature"
---

## Summary

## Background

## Scope

## Acceptance Criteria

- [ ]

## Technical Notes

### DDD Checklist

- [ ] Request DTO와 Command를 분리합니다.
- [ ] Entity를 Controller에서 직접 반환하지 않습니다.
- [ ] Response DTO를 사용합니다.
- [ ] 필요한 경우 Result를 사용합니다.
- [ ] 도메인별 패키지 경계를 지킵니다.
- [ ] 다른 도메인의 Entity 직접 참조를 피합니다.

## Test Notes

## Branch Name

`feat/{issue-number}-description`

## Commit Example

`feat: #{issue-number} 작업 내용`

## Related Issue

## Checklist

- [ ] 오류가 없는 코드만 PR로 보냅니다.
- [ ] Docker 환경에서 동작하지 않는 코드는 머지하지 않습니다.
- [ ] Secret Key와 `.env` 파일은 절대 커밋하지 않습니다.
