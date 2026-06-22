package com.faithlog.batch.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.batch.application.AutomaticNotificationService;
import com.faithlog.batch.application.FcmTokenCleanupService;
import com.faithlog.batch.application.PendingNotificationRecoveryService;
import com.faithlog.batch.application.PollAutomationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
	private PollAutomationService pollAutomationService;

	@MockitoBean
	private FcmTokenCleanupService fcmTokenCleanupService;

	@MockitoBean
	private AutomaticNotificationService automaticNotificationService;

	@MockitoBean
	private PendingNotificationRecoveryService pendingNotificationRecoveryService;

	@Test
	void scheduler_context_starts_with_task_scheduler_and_async_dispatch_executor() {
		assertThat(scheduledJobs).isNotNull();
	}
}
