package com.faithlog.weeklymaterial.infrastructure;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.weeklymaterial.infrastructure.scheduler.WeeklyMaterialScheduledJobs;
import com.faithlog.weeklymaterial.service.WeeklyMaterialNotificationOutboxProcessor;
import com.faithlog.weeklymaterial.service.WeeklyMaterialRetentionService;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.annotation.AnnotatedElementUtils;

class WeeklyMaterialScheduledJobsTest {
	@Test
	void retentionRunsAtAsiaSeoulMidnight() throws Exception {
		Scheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(
			WeeklyMaterialScheduledJobs.class.getMethod("physicallyDeleteExpiredMaterials"), Scheduled.class);
		org.assertj.core.api.Assertions.assertThat(scheduled).isNotNull();
		org.assertj.core.api.Assertions.assertThat(scheduled.cron())
			.isEqualTo("${faithlog.scheduler.weekly-material-retention-cron:0 0 0 * * *}");
		org.assertj.core.api.Assertions.assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
	}

	@Test
	void selectsAndDeletesDueRowsUsingAsiaSeoulDate() {
		WeeklyMaterialNotificationOutboxRepositoryPort outboxes =
			mock(WeeklyMaterialNotificationOutboxRepositoryPort.class);
		WeeklyMaterialNotificationOutboxProcessor processor = mock(WeeklyMaterialNotificationOutboxProcessor.class);
		WeeklyMaterialRepositoryPort materials = mock(WeeklyMaterialRepositoryPort.class);
		WeeklyMaterialRetentionService retention = mock(WeeklyMaterialRetentionService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-08-03T15:00:00Z"), ZoneOffset.UTC);
		when(materials.findDuePhysicalDeletionIds(any(), any(Pageable.class))).thenReturn(List.of(10L, 11L));
		WeeklyMaterialScheduledJobs jobs = new WeeklyMaterialScheduledJobs(
			outboxes, processor, materials, retention, clock);

		jobs.physicallyDeleteExpiredMaterials();

		verify(materials).findDuePhysicalDeletionIds(LocalDate.of(2026, 8, 4),
			org.springframework.data.domain.PageRequest.of(0, 100));
		verify(retention).deleteIfDue(10L, LocalDate.of(2026, 8, 4));
		verify(retention).deleteIfDue(11L, LocalDate.of(2026, 8, 4));
	}
}
