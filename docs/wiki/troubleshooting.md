<!-- daily-resume-monitor:start:troubleshooting:2026-06-16 -->
## 2026-06-16 Automated Review

- Problem: No troubleshooting item was promoted from this run without verified symptoms and root cause.
- Symptoms: Not recorded.
- Root cause: Not recorded.
- Fix: Not recorded.
- Validation: build/test-results/test/TEST-com.faithlog.FaithLogApplicationTests.xml: verified pass; tests=1, passed=1, failures=0, errors=0, skipped=0
- Remaining risk: Transcript source and health/latency target remain pending decisions.
<!-- daily-resume-monitor:end:troubleshooting:2026-06-16 -->

## 2026-06-17 #27 Auth JWT Test Context

- Problem: 인증 구현 후 `JwtProvider`와 MVC slice 테스트 context가 실패.
- Symptoms: `No default constructor found`, `No qualifying bean of type JwtProvider`, `No qualifying bean of type AccessTokenBlacklistChecker`.
- Root cause: `JwtProvider`에 복수 생성자가 있었지만 Spring 주입 생성자가 명시되지 않았고, `@WebMvcTest`가 filter 의존성 전체를 로드하지 않음.
- Fix: 운영 생성자에 `@Autowired`를 명시하고, `AuthControllerTest`에서 `JwtProvider`와 `AccessTokenBlacklistChecker`를 mock bean으로 분리.
- Validation: `./gradlew test` 성공.
- Remaining risk: #28에서 Redis blacklist 저장/삭제 구현 시 `AccessTokenBlacklistChecker` 실제 구현과 통합 테스트 필요.
