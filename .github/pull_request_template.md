## Summary

- 

## Git Flow

- Base branch: `develop`
- Issue:
- Branch:

## Type

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Test
- [ ] Docs
- [ ] Build/CI
- [ ] Chore

## Domain

- [ ] user
- [ ] campus
- [ ] devotion
- [ ] poll
- [ ] billing
- [ ] notification
- [ ] global
- [ ] infra/docker

## Changes

- 

## API Changes

- [ ] No API changes
- [ ] New endpoint
- [ ] Changed endpoint
- [ ] Removed endpoint
- [ ] Swagger/OpenAPI updated

## Database Changes

- [ ] No database changes
- [ ] Entity changed
- [ ] Migration added
- [ ] Seed/test data changed

## Redis / External Integration

- [ ] No Redis or external integration changes
- [ ] Redis key/TTL behavior changed
- [ ] FCM behavior changed
- [ ] JWT/security behavior changed

## Testing

- [ ] Unit tests added or updated
- [ ] Integration tests added or updated
- [ ] Manual test completed
- [ ] Not tested

## Checklist

- [ ] PR target branch is `develop`
- [ ] Branch name follows `<type>/<issue-number>-<short-description>`
- [ ] Commit messages follow `<type>: #<issue-number> <message>`
- [ ] The application runs locally with Docker when applicable
- [ ] Request DTO is not passed directly into application service
- [ ] Entity is not returned directly from controller
- [ ] Create/update flow uses Command objects where appropriate
- [ ] Domain changes are expressed through entity methods such as `create`, `change`, `submit`, or `close`
- [ ] Cross-domain references use IDs instead of direct entity references where possible
- [ ] PostgreSQL uses Spring Boot auto-configuration, not `PostgreSqlConfig.java`
- [ ] Redis implementation is located under the owning domain's `infrastructure/redis`
