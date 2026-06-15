---
name: Release Task
about: 릴리즈 준비와 배포 전 점검
title: "[Release] "
labels: "release"
---

## Summary

## Background

## Scope

## Acceptance Criteria

- [ ]

## Technical Notes

## Test Notes

## Branch Name

`release/{issue-number}-description`

## Commit Example

`release: #{issue-number} 작업 내용`

## Related Issue

## Pre-release Checklist

- [ ] 모든 필수 PR이 `develop`에 머지되었습니다.
- [ ] Docker 환경에서 정상 동작합니다.
- [ ] Swagger/API 확인을 완료했습니다.
- [ ] `.env`와 Secret Key가 노출되지 않았습니다.
- [ ] 릴리즈 노트를 작성했습니다.
- [ ] 포트폴리오 로그를 업데이트했습니다.
