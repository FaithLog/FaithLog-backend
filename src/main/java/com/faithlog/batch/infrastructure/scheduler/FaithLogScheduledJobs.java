package com.faithlog.batch.infrastructure.scheduler;

import com.faithlog.batch.service.DataRetentionCleanupService;
import com.faithlog.batch.service.DevotionMissingNotificationService;
import com.faithlog.batch.service.DueCoffeePollClosureService;
import com.faithlog.batch.service.FcmTokenCleanupService;
import com.faithlog.batch.service.PendingNotificationRecoveryService;
import com.faithlog.batch.service.PaymentUnpaidNotificationService;
import com.faithlog.batch.service.PollMissingNotificationService;
import com.faithlog.batch.service.ScheduledPollCreationService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "faithlog.scheduler", name = "enabled", havingValue = "true")
public class FaithLogScheduledJobs {

	private static final Logger log = LoggerFactory.getLogger(FaithLogScheduledJobs.class);
	private static final String SEOUL_ZONE = "Asia/Seoul";

	private final ScheduledPollCreationService scheduledPollCreationService;
	private final DueCoffeePollClosureService dueCoffeePollClosureService;
	private final DataRetentionCleanupService dataRetentionCleanupService;
	private final FcmTokenCleanupService fcmTokenCleanupService;
	private final DevotionMissingNotificationService devotionMissingNotificationService;
	private final PollMissingNotificationService pollMissingNotificationService;
	private final PaymentUnpaidNotificationService paymentUnpaidNotificationService;
	private final PendingNotificationRecoveryService pendingNotificationRecoveryService;

	public FaithLogScheduledJobs(
		ScheduledPollCreationService scheduledPollCreationService,
		DueCoffeePollClosureService dueCoffeePollClosureService,
		DataRetentionCleanupService dataRetentionCleanupService,
		FcmTokenCleanupService fcmTokenCleanupService,
		DevotionMissingNotificationService devotionMissingNotificationService,
		PollMissingNotificationService pollMissingNotificationService,
		PaymentUnpaidNotificationService paymentUnpaidNotificationService,
		PendingNotificationRecoveryService pendingNotificationRecoveryService
	) {
		this.scheduledPollCreationService = scheduledPollCreationService;
		this.dueCoffeePollClosureService = dueCoffeePollClosureService;
		this.dataRetentionCleanupService = dataRetentionCleanupService;
		this.fcmTokenCleanupService = fcmTokenCleanupService;
		this.devotionMissingNotificationService = devotionMissingNotificationService;
		this.pollMissingNotificationService = pollMissingNotificationService;
		this.paymentUnpaidNotificationService = paymentUnpaidNotificationService;
		this.pendingNotificationRecoveryService = pendingNotificationRecoveryService;
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.poll-auto-create-delay-ms:60000}", zone = SEOUL_ZONE)
	public void createDuePolls() {
		runJob("poll-auto-create", () -> scheduledPollCreationService.createDuePolls(Instant.now()));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.coffee-poll-close-delay-ms:60000}", zone = SEOUL_ZONE)
	public void closeDueCoffeePolls() {
		runJob("coffee-poll-close", () -> dueCoffeePollClosureService.closeDueCoffeePolls(Instant.now()));
	}

	@Scheduled(cron = "${faithlog.scheduler.data-retention-cleanup-cron:0 30 4 * * *}", zone = SEOUL_ZONE)
	public void cleanupDataRetention() {
		runJob("data-retention-cleanup", () -> dataRetentionCleanupService.cleanupDueData(Instant.now()).totalDeleted());
	}

	@Scheduled(cron = "${faithlog.scheduler.fcm-token-cleanup-cron:0 20 3 * * *}", zone = SEOUL_ZONE)
	public void cleanupStaleFcmTokens() {
		runJob("fcm-token-cleanup", () -> fcmTokenCleanupService.deactivateStaleTokens(Instant.now()));
	}

	@Scheduled(cron = "${faithlog.scheduler.devotion-missing-cron:0 0 11 * * *}", zone = SEOUL_ZONE)
	public void sendDevotionMissingReminders() {
		runJob("devotion-missing", () -> devotionMissingNotificationService.sendDevotionMissingReminders(Instant.now()));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.poll-missing-delay-ms:60000}", zone = SEOUL_ZONE)
	public void sendPollMissingReminders() {
		runJob("poll-missing", () -> pollMissingNotificationService.sendPollMissingReminders(Instant.now()));
	}

	@Scheduled(cron = "${faithlog.scheduler.payment-unpaid-cron:0 0 12 * * *}", zone = SEOUL_ZONE)
	public void sendPaymentUnpaidReminders() {
		runJob("payment-unpaid", () -> paymentUnpaidNotificationService.sendPaymentUnpaidReminders(Instant.now()));
	}

	@Scheduled(fixedDelayString = "${faithlog.scheduler.pending-notification-recovery-delay-ms:60000}", zone = SEOUL_ZONE)
	public void reprocessPendingNotificationLogs() {
		runJob("pending-notification-reprocess", () -> pendingNotificationRecoveryService.reprocessStalePendingLogs(Instant.now()));
	}

	private void runJob(String jobName, ScheduledJob job) {
		try {
			int changedCount = job.run();
			log.info("FaithLog scheduled job completed: jobName={}, changedCount={}", jobName, changedCount);
		} catch (RuntimeException exception) {
			log.error("FaithLog scheduled job failed: jobName={}", jobName, exception);
		}
	}

	@FunctionalInterface
	private interface ScheduledJob {
		int run();
	}
}
