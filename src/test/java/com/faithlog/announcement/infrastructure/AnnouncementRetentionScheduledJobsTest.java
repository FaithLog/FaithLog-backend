package com.faithlog.announcement.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.infrastructure.scheduler.AnnouncementScheduledJobs;
import com.faithlog.announcement.service.AnnouncementNotificationOutboxProcessor;
import com.faithlog.announcement.service.AnnouncementRetentionService;
import com.faithlog.announcement.service.ScheduledAnnouncementPublisher;
import com.faithlog.announcement.service.port.AnnouncementNotificationOutboxRepositoryPort;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.annotation.AnnotatedElementUtils;

class AnnouncementRetentionScheduledJobsTest {
	@Test
	void retentionRunsAtAsiaSeoulMidnight() throws Exception {
		Scheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(
			AnnouncementScheduledJobs.class.getMethod("physicallyDeleteExpiredAnnouncements"), Scheduled.class);
		org.assertj.core.api.Assertions.assertThat(scheduled).isNotNull();
		org.assertj.core.api.Assertions.assertThat(scheduled.cron())
			.isEqualTo("${faithlog.scheduler.announcement-retention-cron:0 0 0 * * *}");
		org.assertj.core.api.Assertions.assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	@Test
	void schedulesDuePhysicalDeletionUsingInjectedClock() {
		AnnouncementRepositoryPort announcements = mock(AnnouncementRepositoryPort.class);
		ScheduledAnnouncementPublisher publisher = mock(ScheduledAnnouncementPublisher.class);
		AnnouncementNotificationOutboxRepositoryPort outboxes =
			mock(AnnouncementNotificationOutboxRepositoryPort.class);
		AnnouncementNotificationOutboxProcessor processor = mock(AnnouncementNotificationOutboxProcessor.class);
		AnnouncementRetentionService retention = mock(AnnouncementRetentionService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-08-04T15:00:00Z"), ZoneOffset.UTC);
		when(announcements.findDuePhysicalDeletionIds(any(), any(Pageable.class))).thenReturn(List.of(10L, 11L));
		AnnouncementScheduledJobs jobs = new AnnouncementScheduledJobs(
			announcements, publisher, outboxes, processor, retention, clock);

		jobs.physicallyDeleteExpiredAnnouncements();

		verify(announcements).findDuePhysicalDeletionIds(clock.instant(),
			org.springframework.data.domain.PageRequest.of(0, 100));
		verify(retention).deleteIfDue(10L, clock.instant());
		verify(retention).deleteIfDue(11L, clock.instant());
	}
}
