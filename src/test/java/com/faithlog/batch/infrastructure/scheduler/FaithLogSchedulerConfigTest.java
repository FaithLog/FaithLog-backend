package com.faithlog.batch.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.batch.service.DataRetentionCleanupService;
import com.faithlog.batch.service.DevotionMissingNotificationService;
import com.faithlog.batch.service.DueCoffeePollClosureService;
import com.faithlog.batch.service.FcmTokenCleanupService;
import com.faithlog.batch.service.PendingNotificationRecoveryService;
import com.faithlog.batch.service.PaymentUnpaidNotificationService;
import com.faithlog.batch.service.PollMissingNotificationService;
import com.faithlog.batch.service.ScheduledPollCreationService;
import com.faithlog.global.observability.OperationalEventPort;
import java.lang.reflect.Method;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
	"faithlog.scheduler.enabled=true",
	"faithlog.scheduler.poll-auto-create-delay-ms=3600000",
	"faithlog.scheduler.coffee-poll-close-delay-ms=3600000",
	"faithlog.scheduler.poll-missing-delay-ms=3600000",
	"faithlog.scheduler.pending-notification-recovery-delay-ms=3600000"
})
@ActiveProfiles("test")
class FaithLogSchedulerConfigTest {

	@Autowired
	private FaithLogScheduledJobs scheduledJobs;

	@MockitoBean
	private DataRetentionCleanupService dataRetentionCleanupService;

	@MockitoBean
	private ScheduledPollCreationService scheduledPollCreationService;

	@MockitoBean
	private DueCoffeePollClosureService dueCoffeePollClosureService;

	@MockitoBean
	private FcmTokenCleanupService fcmTokenCleanupService;

	@MockitoBean
	private DevotionMissingNotificationService devotionMissingNotificationService;

	@MockitoBean
	private PollMissingNotificationService pollMissingNotificationService;

	@MockitoBean
	private PaymentUnpaidNotificationService paymentUnpaidNotificationService;

	@MockitoBean
	private PendingNotificationRecoveryService pendingNotificationRecoveryService;

	@MockitoBean
	private OperationalEventPort operationalEvents;

	@Test
	void scheduler_context_starts_with_task_scheduler_and_async_dispatch_executor() {
		assertThat(scheduledJobs).isNotNull();
	}

	@Test
	void dataRetentionCleanup_uses_seoul_four_thirty_cron() throws Exception {
		Method method = FaithLogScheduledJobs.class.getDeclaredMethod("cleanupDataRetention");
		Scheduled scheduled = method.getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.cron()).isEqualTo("${faithlog.scheduler.data-retention-cleanup-cron:0 30 4 * * *}");
		assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	@Test
	void scheduler_records_a_bounded_success_heartbeat() {
		when(scheduledPollCreationService.createDuePolls(any())).thenReturn(2);

		scheduledJobs.createDuePolls();

		verify(operationalEvents).schedulerSuccess("poll-auto-create", 2);
	}

	@Test
	void scheduler_records_failure_without_exception_details() {
		doThrow(new IllegalStateException("private provider detail"))
			.when(scheduledPollCreationService).createDuePolls(any());

		scheduledJobs.createDuePolls();

		verify(operationalEvents).schedulerFailure("poll-auto-create");
	}
}
