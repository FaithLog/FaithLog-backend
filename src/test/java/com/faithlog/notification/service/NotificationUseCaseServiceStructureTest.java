package com.faithlog.notification.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class NotificationUseCaseServiceStructureTest {

	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");
	private static final Path SERVICE_ROOT = MAIN_ROOT.resolve("notification/service");

	@Test
	void tokenAndNotificationRequestCommandsOwnTheirTransactionBoundaries() {
		String tokenCommandService = read(SERVICE_ROOT.resolve("FcmTokenCommandService.java"));
		String requestCommandService = read(SERVICE_ROOT.resolve("NotificationRequestCommandService.java"));
		String logQueryService = read(SERVICE_ROOT.resolve("NotificationLogQueryService.java"));

		assertAll(
			() -> assertTransactional(tokenCommandService, "registerToken"),
			() -> assertTransactional(tokenCommandService, "deactivateToken"),
			() -> assertTransactional(tokenCommandService, "deactivateCurrentDevice"),
			() -> assertTransactional(tokenCommandService, "deactivateStaleTokens"),
			() -> assertTransactional(requestCommandService, "requestNotification"),
			() -> assertTransactional(requestCommandService, "requestAutomaticNotification"),
			() -> assertReadOnlyTransactional(logQueryService, "searchLogs")
		);
	}

	@Test
	void controllersAndBatchCallDedicatedUseCaseServicesDirectly() {
		String fcmController = read(MAIN_ROOT.resolve("notification/controller/FcmTokenController.java"));
		String adminController = read(MAIN_ROOT.resolve("notification/controller/AdminNotificationController.java"));
		String automaticNotificationService = read(MAIN_ROOT.resolve("batch/service/AutomaticNotificationService.java"));
		String cleanupService = read(MAIN_ROOT.resolve("batch/service/FcmTokenCleanupService.java"));
		String recoveryService = read(MAIN_ROOT.resolve("batch/service/PendingNotificationRecoveryService.java"));
		String dispatchAdapter = read(MAIN_ROOT.resolve("notification/infrastructure/fcm/AsyncNotificationDispatchAdapter.java"));

		assertAll(
			() -> assertTrue(fcmController.contains("FcmTokenCommandService")),
			() -> assertFalse(fcmController.contains("FcmTokenService")),
			() -> assertTrue(adminController.contains("NotificationRequestCommandService")),
			() -> assertFalse(adminController.contains("NotificationService")),
			() -> assertTrue(adminController.contains("NotificationLogQueryService")),
			() -> assertTrue(automaticNotificationService.contains("NotificationRequestCommandService")),
			() -> assertFalse(automaticNotificationService.contains("NotificationLogRepository")),
			() -> assertFalse(automaticNotificationService.contains("UserFcmTokenRepository")),
			() -> assertFalse(automaticNotificationService.contains("NotificationDispatchPort")),
			() -> assertTrue(cleanupService.contains("FcmTokenCommandService")),
			() -> assertFalse(cleanupService.contains("UserFcmTokenRepository")),
			() -> assertTrue(recoveryService.contains("NotificationDeliveryWorker")),
			() -> assertTrue(dispatchAdapter.contains("NotificationDeliveryWorker"))
		);
	}

	@Test
	void compatibilityFacadesAreThinDelegatesWithoutRulesOrTransactions() {
		String fcmFacade = read(SERVICE_ROOT.resolve("FcmTokenService.java"));
		String notificationFacade = read(SERVICE_ROOT.resolve("NotificationService.java"));

		assertAll(
			() -> assertThinFacade(fcmFacade),
			() -> assertTrue(fcmFacade.contains("FcmTokenCommandService")),
			() -> assertFalse(fcmFacade.contains("CurrentDeviceFcmTokenDeactivationPort")),
			() -> assertThinFacade(notificationFacade),
			() -> assertTrue(notificationFacade.contains("NotificationRequestCommandService"))
		);
	}

	@Test
	void currentDevicePortIsImplementedByTheTokenCommandBoundary() {
		String tokenCommandService = read(SERVICE_ROOT.resolve("FcmTokenCommandService.java"));

		assertAll(
			() -> assertTrue(tokenCommandService.contains("implements CurrentDeviceFcmTokenDeactivationPort")),
			() -> assertTrue(tokenCommandService.contains("deactivateCurrentDevice"))
		);
	}

	@Test
	void dedicatedServicesDoNotUseFacadesOrFormApplicationServiceCycles() {
		String tokenCommandService = read(SERVICE_ROOT.resolve("FcmTokenCommandService.java"));
		String requestCommandService = read(SERVICE_ROOT.resolve("NotificationRequestCommandService.java"));
		String deliveryWorker = read(SERVICE_ROOT.resolve("NotificationDeliveryWorker.java"));
		String logQueryService = read(SERVICE_ROOT.resolve("NotificationLogQueryService.java"));

		assertAll(
			() -> assertFalse(tokenCommandService.contains("FcmTokenService")),
			() -> assertFalse(tokenCommandService.contains("NotificationRequestCommandService")),
			() -> assertFalse(tokenCommandService.contains("NotificationDeliveryWorker")),
			() -> assertFalse(tokenCommandService.contains("NotificationLogQueryService")),
			() -> assertFalse(requestCommandService.contains("NotificationService")),
			() -> assertFalse(requestCommandService.contains("FcmTokenCommandService")),
			() -> assertFalse(requestCommandService.contains("NotificationDeliveryWorker")),
			() -> assertFalse(requestCommandService.contains("NotificationLogQueryService")),
			() -> assertFalse(deliveryWorker.contains("NotificationService")),
			() -> assertFalse(deliveryWorker.contains("FcmTokenService")),
			() -> assertFalse(deliveryWorker.contains("NotificationRequestCommandService")),
			() -> assertFalse(deliveryWorker.contains("NotificationLogQueryService")),
			() -> assertFalse(logQueryService.contains("NotificationService")),
			() -> assertFalse(logQueryService.contains("FcmTokenService")),
			() -> assertFalse(logQueryService.contains("NotificationRequestCommandService")),
			() -> assertFalse(logQueryService.contains("NotificationDeliveryWorker"))
		);
	}

	@Test
	void applicationServicesDoNotDependOnRedisOrFirebaseSdkTypes() throws IOException {
		try (Stream<Path> sources = Files.list(SERVICE_ROOT)) {
			List<String> contents = sources
				.filter(path -> path.toString().endsWith(".java"))
				.map(this::read)
				.toList();

			assertAll(contents.stream().map(content -> () -> {
				assertFalse(content.contains("RedisTemplate"));
				assertFalse(content.contains("StringRedisTemplate"));
				assertFalse(content.contains("com.google.firebase"));
			}));
		}
	}

	private void assertTransactional(String source, String method) {
		assertTrue(Pattern.compile(
			"@Transactional\\s+public\\s+[^\\n{]+\\s+" + Pattern.quote(method) + "\\s*\\("
		).matcher(source).find(), method + "가 write transaction을 직접 소유해야 합니다.");
	}

	private void assertReadOnlyTransactional(String source, String method) {
		assertTrue(Pattern.compile(
			"@Transactional\\(readOnly\\s*=\\s*true\\)\\s+public\\s+[^\\n{]+\\s+"
				+ Pattern.quote(method)
				+ "\\s*\\("
		).matcher(source).find(), method + "가 readOnly transaction을 직접 소유해야 합니다.");
	}

	private void assertThinFacade(String source) {
		assertAll(
			() -> assertFalse(source.contains("Repository")),
			() -> assertFalse(source.contains("@Transactional")),
			() -> assertFalse(source.contains("BusinessException")),
			() -> assertFalse(source.contains("ErrorCode")),
			() -> assertFalse(source.contains("NotificationLog.pending(")),
			() -> assertFalse(source.contains("NotificationLog.skipped(")),
			() -> assertFalse(source.contains("UserFcmToken.create("))
		);
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
