package com.faithlog.batch.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class BatchSchedulerResponsibilityStructureTest {

	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");
	private static final Path BATCH_ROOT = MAIN_ROOT.resolve("batch/service");
	private static final Path SCHEDULER = MAIN_ROOT.resolve("batch/infrastructure/scheduler/FaithLogScheduledJobs.java");

	@Test
	void pollCreationAndCoffeeClosureHaveDedicatedUseCaseBoundaries() {
		String creation = readRequired("ScheduledPollCreationService.java");
		String closure = readRequired("DueCoffeePollClosureService.java");
		String facade = readRequired("PollAutomationService.java");

		assertAll(
			() -> assertTrue(creation.contains("ScheduledPollFactory")),
			() -> assertTrue(creation.contains("TransactionTemplate")),
			() -> assertTrue(creation.contains("NotificationLockService")),
			() -> assertFalse(creation.contains("CoffeePollSettlement")),
			() -> assertTrue(closure.contains("CoffeePollSettlementCommandService")),
			() -> assertTrue(closure.contains("TransactionTemplate")),
			() -> assertTrue(closure.contains("NotificationLockService")),
			() -> assertFalse(closure.contains("ScheduledPollFactory")),
			() -> assertThinFacade(facade),
			() -> assertTrue(facade.contains("ScheduledPollCreationService")),
			() -> assertTrue(facade.contains("DueCoffeePollClosureService"))
		);
	}

	@Test
	void automaticNotificationJobsHaveDedicatedUseCaseBoundaries() {
		String devotion = readRequired("DevotionMissingNotificationService.java");
		String poll = readRequired("PollMissingNotificationService.java");
		String payment = readRequired("PaymentUnpaidNotificationService.java");
		String facade = readRequired("AutomaticNotificationService.java");

		assertAll(
			() -> assertTrue(devotion.contains("WeeklyDevotionRecordRepository")),
			() -> assertFalse(devotion.contains("PollRepository")),
			() -> assertFalse(devotion.contains("ChargeItemRepository")),
			() -> assertTrue(poll.contains("PollRepository")),
			() -> assertTrue(poll.contains("PollResponseRepository")),
			() -> assertFalse(poll.contains("WeeklyDevotionRecordRepository")),
			() -> assertFalse(poll.contains("ChargeItemRepository")),
			() -> assertTrue(payment.contains("ChargeItemRepository")),
			() -> assertFalse(payment.contains("WeeklyDevotionRecordRepository")),
			() -> assertFalse(payment.contains("PollRepository")),
			() -> assertTrue(devotion.contains("NotificationRequestCommandService")),
			() -> assertTrue(poll.contains("NotificationRequestCommandService")),
			() -> assertTrue(payment.contains("NotificationRequestCommandService")),
			() -> assertThinFacade(facade)
		);
	}

	@Test
	void schedulerCallsOnlyDedicatedJobServices() {
		String scheduler = read(SCHEDULER);

		assertAll(
			() -> assertTrue(scheduler.contains("ScheduledPollCreationService")),
			() -> assertTrue(scheduler.contains("DueCoffeePollClosureService")),
			() -> assertTrue(scheduler.contains("DevotionMissingNotificationService")),
			() -> assertTrue(scheduler.contains("PollMissingNotificationService")),
			() -> assertTrue(scheduler.contains("PaymentUnpaidNotificationService")),
			() -> assertFalse(scheduler.contains("PollAutomationService")),
			() -> assertFalse(scheduler.contains("AutomaticNotificationService")),
			() -> assertFalse(scheduler.contains("Repository")),
			() -> assertFalse(scheduler.contains("TransactionTemplate")),
			() -> assertNoExternalSdkDependency(scheduler)
		);
	}

	@Test
	void cleanupAndRecoveryStaySeparateAndOwnExistingTransactionBoundaries() {
		String tokenCleanup = readRequired("FcmTokenCleanupService.java");
		String retention = readRequired("DataRetentionCleanupService.java");
		String recovery = readRequired("PendingNotificationRecoveryService.java");

		assertAll(
			() -> assertTrue(tokenCleanup.contains("UserFcmTokenRepository")),
			() -> assertTrue(tokenCleanup.contains("@Transactional")),
			() -> assertFalse(tokenCleanup.contains("FcmTokenCommandService")),
			() -> assertTrue(retention.contains("TransactionTemplate")),
			() -> assertFalse(retention.contains("UserFcmTokenRepository")),
			() -> assertTrue(recovery.contains("TransactionTemplate")),
			() -> assertTrue(recovery.contains("NotificationDeliveryWorker")),
			() -> assertFalse(recovery.contains("UserFcmTokenRepository")),
			() -> assertNoExternalSdkDependency(tokenCleanup),
			() -> assertNoExternalSdkDependency(retention),
			() -> assertNoExternalSdkDependency(recovery)
		);
	}

	@Test
	void dedicatedBatchServicesDoNotDependOnCompatibilityFacadesOrFormCycles() throws IOException {
		List<String> dedicatedServices = List.of(
			"ScheduledPollCreationService.java",
			"DueCoffeePollClosureService.java",
			"DevotionMissingNotificationService.java",
			"PollMissingNotificationService.java",
			"PaymentUnpaidNotificationService.java",
			"FcmTokenCleanupService.java",
			"DataRetentionCleanupService.java",
			"PendingNotificationRecoveryService.java"
		);
		List<String> facadeNames = List.of("PollAutomationService", "AutomaticNotificationService");

		assertAll(dedicatedServices.stream().map(fileName -> () -> {
			String source = readRequired(fileName);
			facadeNames.forEach(facade -> assertFalse(source.contains(facade), fileName + " -> " + facade));
			assertNoExternalSdkDependency(source);
		}));
	}

	private String readRequired(String fileName) {
		Path source = BATCH_ROOT.resolve(fileName);
		assertTrue(Files.exists(source), fileName + "가 필요합니다.");
		return read(source);
	}

	private void assertThinFacade(String source) {
		assertAll(
			() -> assertFalse(source.contains("Repository")),
			() -> assertFalse(source.contains("@Transactional")),
			() -> assertFalse(source.contains("TransactionTemplate")),
			() -> assertFalse(source.contains("NotificationLockService")),
			() -> assertFalse(source.contains("BusinessException")),
			() -> assertNoExternalSdkDependency(source)
		);
	}

	private void assertNoExternalSdkDependency(String source) {
		assertAll(
			() -> assertFalse(source.contains("RedisTemplate")),
			() -> assertFalse(source.contains("StringRedisTemplate")),
			() -> assertFalse(source.contains("com.google.firebase"))
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
