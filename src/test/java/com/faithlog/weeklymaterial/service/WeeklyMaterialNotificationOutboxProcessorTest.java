package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRecipientPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeeklyMaterialNotificationOutboxProcessorTest {
	private final WeeklyMaterialNotificationOutboxRepositoryPort outboxes =
		mock(WeeklyMaterialNotificationOutboxRepositoryPort.class);
	private final WeeklyMaterialRecipientPort recipients = mock(WeeklyMaterialRecipientPort.class);
	private final NotificationRequestCommandService notifications = mock(NotificationRequestCommandService.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);
	private final WeeklyMaterialNotificationOutboxProcessor processor =
		new WeeklyMaterialNotificationOutboxProcessor(outboxes, recipients, notifications, clock);

	@Test
	void sendsApprovedSundaySermonSharingSheetCopyToActiveMembersExceptUploader() {
		var outbox = WeeklyMaterialNotificationOutbox.create(
			1L, 10L, LocalDate.of(2026, 8, 3), 100L);
		when(outboxes.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));
		when(recipients.findActiveMemberUserIds(1L)).thenReturn(List.of(100L, 101L, 102L, 102L));

		assertThat(processor.process(1L)).isTrue();

		verify(notifications).requestRequiredAutomaticNotification(org.mockito.ArgumentMatchers.argThat(command ->
			command.targetUserIds().equals(List.of(101L, 102L))
				&& command.title().equals("새 주일설교 나눔지가 등록되었어요")
				&& command.body().equals("2026-08-03 주차 주일설교 나눔지를 확인해 주세요")
				&& command.data().get("eventType").equals("WEEKLY_SHARING_SHEET_PUBLISHED")
				&& command.data().keySet().equals(java.util.Set.of(
					"eventType", "campusId", "weeklyMaterialId", "weekStartDate"))));
		assertThat(outbox.isProcessed()).isTrue();
	}

	@Test
	void leavesOutboxPendingWhenNotificationRequestFailsForRetry() {
		var outbox = WeeklyMaterialNotificationOutbox.create(
			1L, 10L, LocalDate.of(2026, 8, 3), 100L);
		when(outboxes.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));
		when(recipients.findActiveMemberUserIds(1L)).thenReturn(List.of(101L));
		org.mockito.Mockito.doThrow(new IllegalStateException("temporary"))
			.when(notifications).requestRequiredAutomaticNotification(any());

		assertThatThrownBy(() -> processor.process(1L)).isInstanceOf(IllegalStateException.class);
		assertThat(outbox.isProcessed()).isFalse();
	}

	@Test
	void processedOutboxIsIdempotent() {
		var outbox = WeeklyMaterialNotificationOutbox.create(
			1L, 10L, LocalDate.of(2026, 8, 3), 100L);
		outbox.markProcessed(clock.instant());
		when(outboxes.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

		assertThat(processor.process(1L)).isFalse();
		verify(notifications, never()).requestRequiredAutomaticNotification(any());
	}
}
