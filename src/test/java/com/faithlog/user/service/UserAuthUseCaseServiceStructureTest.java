package com.faithlog.user.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class UserAuthUseCaseServiceStructureTest {

	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");
	private static final Path SERVICE_ROOT = MAIN_ROOT.resolve("user/service");

	@Test
	void userAndAuthUseCasesOwnTheirTransactionBoundariesDirectly() {
		Map<String, TransactionalUseCase> responsibilities = Map.of(
			"SignupCommandService.java", write("signup"),
			"LoginCommandService.java", write("login"),
			"RefreshTokenRotationService.java", write("refresh"),
			"LogoutCommandService.java", write("logout"),
			"UserMeQueryService.java", readOnly("getCurrentUser"),
			"AccountWithdrawalCommandService.java", write("deleteMyAccount")
		);

		assertAll(responsibilities.entrySet().stream().map(entry -> () -> {
			Path source = SERVICE_ROOT.resolve(entry.getKey());
			assertTrue(Files.exists(source), entry.getKey() + "가 필요합니다.");
			assertTrue(
				entry.getValue().pattern().matcher(read(source)).find(),
				entry.getValue().method() + "가 기존 transaction 경계를 직접 소유해야 합니다."
			);
		}));
	}

	@Test
	void controllersCallDedicatedUseCaseServicesDirectly() {
		String authController = read(MAIN_ROOT.resolve("user/controller/AuthController.java"));
		String userMeController = read(MAIN_ROOT.resolve("user/controller/UserMeController.java"));

		assertAll(
			() -> assertTrue(authController.contains("SignupCommandService")),
			() -> assertTrue(authController.contains("LoginCommandService")),
			() -> assertTrue(authController.contains("RefreshTokenRotationService")),
			() -> assertTrue(authController.contains("LogoutCommandService")),
			() -> assertFalse(authController.contains("AuthService")),
			() -> assertTrue(userMeController.contains("UserMeQueryService")),
			() -> assertTrue(userMeController.contains("AccountWithdrawalCommandService")),
			() -> assertFalse(userMeController.contains("AuthService")),
			() -> assertFalse(userMeController.contains("UserAccountService"))
		);
	}

	@Test
	void compatibilityFacadesAreThinDelegatesWithoutRulesOrTransactions() {
		String authFacade = read(SERVICE_ROOT.resolve("AuthService.java"));
		String accountFacade = read(SERVICE_ROOT.resolve("UserAccountService.java"));

		assertAll(
			() -> assertThinFacade(authFacade),
			() -> assertTrue(authFacade.contains("SignupCommandService")),
			() -> assertTrue(authFacade.contains("LoginCommandService")),
			() -> assertTrue(authFacade.contains("RefreshTokenRotationService")),
			() -> assertTrue(authFacade.contains("LogoutCommandService")),
			() -> assertTrue(authFacade.contains("UserMeQueryService")),
			() -> assertThinFacade(accountFacade),
			() -> assertTrue(accountFacade.contains("AccountWithdrawalCommandService"))
		);
	}

	@Test
	void tokenCampusAndWithdrawalCollaborationsStayBehindCohesiveSupportAndPortBoundaries() {
		String login = read(SERVICE_ROOT.resolve("LoginCommandService.java"));
		String refresh = read(SERVICE_ROOT.resolve("RefreshTokenRotationService.java"));
		String logout = read(SERVICE_ROOT.resolve("LogoutCommandService.java"));
		String me = read(SERVICE_ROOT.resolve("UserMeQueryService.java"));
		String withdrawal = read(SERVICE_ROOT.resolve("AccountWithdrawalCommandService.java"));
		String fcmCommands = read(MAIN_ROOT.resolve("notification/service/FcmTokenCommandService.java"));

		assertAll(
			() -> assertTrue(login.contains("AuthTokenIssuanceSupport")),
			() -> assertTrue(refresh.contains("AuthTokenIssuanceSupport")),
			() -> assertTrue(login.contains("CampusMembershipQuerySupport")),
			() -> assertTrue(me.contains("CampusMembershipQuerySupport")),
			() -> assertTrue(logout.contains("UserSessionRevocationSupport")),
			() -> assertTrue(withdrawal.contains("UserSessionRevocationSupport")),
			() -> assertTrue(withdrawal.contains("AccountSoftDeletionSupport")),
			() -> assertTrue(withdrawal.contains("UserFcmTokenDeactivationPort")),
			() -> assertFalse(withdrawal.contains("UserFcmTokenRepository")),
			() -> assertTrue(fcmCommands.contains("UserFcmTokenDeactivationPort")),
			() -> assertTrue(fcmCommands.contains("deactivateAllForUser"))
		);
	}

	@Test
	void dedicatedServicesDoNotDependOnFacadesOrOtherDedicatedServices() {
		List<String> services = List.of(
			"SignupCommandService",
			"LoginCommandService",
			"RefreshTokenRotationService",
			"LogoutCommandService",
			"UserMeQueryService",
			"AccountWithdrawalCommandService"
		);

		assertAll(services.stream().map(service -> () -> {
			String content = read(SERVICE_ROOT.resolve(service + ".java"));
			assertFalse(content.contains("AuthService"), service + "가 AuthService에 의존하면 안 됩니다.");
			assertFalse(content.contains("UserAccountService"), service + "가 UserAccountService에 의존하면 안 됩니다.");
			services.stream()
				.filter(other -> !other.equals(service))
				.forEach(other -> assertFalse(
					content.contains(other),
					service + "가 다른 public use case service " + other + "에 의존하면 안 됩니다."
				));
		}));
	}

	@Test
	void userApplicationServicesDoNotLeakRedisJwtLibraryOrBcryptImplementationTypes() throws IOException {
		try (Stream<Path> sources = Files.list(SERVICE_ROOT)) {
			List<String> contents = sources
				.filter(path -> path.toString().endsWith(".java"))
				.map(this::read)
				.toList();

			assertAll(contents.stream().map(content -> () -> {
				assertFalse(content.contains("RedisTemplate"));
				assertFalse(content.contains("StringRedisTemplate"));
				assertFalse(content.contains("io.jsonwebtoken.Jwts"));
				assertFalse(content.contains("BCryptPasswordEncoder"));
			}));
		}
	}

	private void assertThinFacade(String source) {
		assertAll(
			() -> assertFalse(source.contains("Repository")),
			() -> assertFalse(source.contains("@Transactional")),
			() -> assertFalse(source.contains("BusinessException")),
			() -> assertFalse(source.contains("ErrorCode")),
			() -> assertFalse(source.contains("JwtProvider")),
			() -> assertFalse(source.contains("PasswordEncoder")),
			() -> assertFalse(source.contains("RefreshTokenStore")),
			() -> assertFalse(source.contains("AccessTokenBlacklistStore"))
		);
	}

	private TransactionalUseCase write(String method) {
		return new TransactionalUseCase(method, Pattern.compile(
			"@Transactional\\s+public\\s+[^\\n{]+\\s+" + Pattern.quote(method) + "\\s*\\("
		));
	}

	private TransactionalUseCase readOnly(String method) {
		return new TransactionalUseCase(method, Pattern.compile(
			"@Transactional\\(readOnly\\s*=\\s*true\\)\\s+public\\s+[^\\n{]+\\s+"
				+ Pattern.quote(method)
				+ "\\s*\\("
		));
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private record TransactionalUseCase(String method, Pattern pattern) {
	}
}
