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
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRecipientPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRecipient;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialOutboxSnapshot;
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
	private final WeeklyMaterialRepositoryPort materials = mock(WeeklyMaterialRepositoryPort.class);
	private final NotificationRequestCommandService notifications = mock(NotificationRequestCommandService.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC);
	private final WeeklyMaterialNotificationOutboxProcessor processor =
		new WeeklyMaterialNotificationOutboxProcessor(outboxes, materials, recipients, notifications, clock);

	@Test
	void sendsApprovedSundaySermonSharingSheetCopyToActiveMembersExceptUploader() {
		var outbox = WeeklyMaterialNotificationOutbox.create(
			1L, 10L, LocalDate.of(2026, 8, 3), 100L);
		when(outboxes.findSnapshotById(1L)).thenReturn(Optional.of(snapshot(outbox)));
		when(materials.findSlotForUpdate(LocalDate.of(2026, 8, 3), WeeklyMaterialType.SUNDAY_SHARING_SHEET))
			.thenReturn(Optional.of(material(10L)));
		when(outboxes.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));
		when(recipients.findAllActiveRecipients()).thenReturn(List.of(
			new WeeklyMaterialRecipient(100L, 1L),
			new WeeklyMaterialRecipient(101L, 1L),
			new WeeklyMaterialRecipient(102L, 2L)));

		assertThat(processor.process(1L)).isTrue();

		verify(notifications).requestRequiredAutomaticNotification(org.mockito.ArgumentMatchers.argThat(command ->
			command.campusId().equals(1L) && command.targetUserIds().equals(List.of(101L))
				&& command.title().equals("새 주일설교 나눔지가 등록되었어요")
				&& command.body().equals("2026-08-03 주차 주일설교 나눔지를 확인해 주세요")
				&& command.data().get("eventType").equals("WEEKLY_SHARING_SHEET_PUBLISHED")
				&& command.data().keySet().equals(java.util.Set.of(
					"eventType", "campusId", "weeklyMaterialId", "weekStartDate"))));
		verify(notifications).requestRequiredAutomaticNotification(org.mockito.ArgumentMatchers.argThat(command ->
			command.campusId().equals(2L) && command.targetUserIds().equals(List.of(102L))
				&& command.data().get("campusId").equals("2")));
		assertThat(outbox.isProcessed()).isTrue();
	}

	@Test
	void leavesOutboxPendingWhenNotificationRequestFailsForRetry() {
		var outbox = WeeklyMaterialNotificationOutbox.create(
			1L, 10L, LocalDate.of(2026, 8, 3), 100L);
		when(outboxes.findSnapshotById(1L)).thenReturn(Optional.of(snapshot(outbox)));
		when(materials.findSlotForUpdate(LocalDate.of(2026, 8, 3), WeeklyMaterialType.SUNDAY_SHARING_SHEET))
			.thenReturn(Optional.of(material(10L)));
		when(outboxes.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));
		when(recipients.findAllActiveRecipients()).thenReturn(List.of(new WeeklyMaterialRecipient(101L, 1L)));
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
		when(outboxes.findSnapshotById(1L)).thenReturn(Optional.of(snapshot(outbox)));
		when(materials.findSlotForUpdate(LocalDate.of(2026, 8, 3), WeeklyMaterialType.SUNDAY_SHARING_SHEET))
			.thenReturn(Optional.of(material(10L)));
		when(outboxes.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

		assertThat(processor.process(1L)).isFalse();
		verify(notifications, never()).requestRequiredAutomaticNotification(any());
	}

	@Test
	void suppressesPendingOutboxWhenMaterialWasDeletedBeforeProcessing() {
		var outbox = WeeklyMaterialNotificationOutbox.create(
			1L, 10L, LocalDate.of(2026, 8, 3), 100L);
		when(outboxes.findSnapshotById(1L)).thenReturn(Optional.of(snapshot(outbox)));
		when(materials.findSlotForUpdate(LocalDate.of(2026, 8, 3), WeeklyMaterialType.SUNDAY_SHARING_SHEET))
			.thenReturn(Optional.empty());
		when(outboxes.findByIdForUpdate(1L)).thenReturn(Optional.of(outbox));

		assertThat(processor.process(1L)).isFalse();

		assertThat(outbox.isProcessed()).isTrue();
		verify(notifications, never()).requestRequiredAutomaticNotification(any());
	}

	private static WeeklyMaterial material(Long id) {
		WeeklyMaterial material = WeeklyMaterial.create(1L, LocalDate.of(2026, 8, 3),
			WeeklyMaterialType.SUNDAY_SHARING_SHEET, 20L, 100L);
		org.springframework.test.util.ReflectionTestUtils.setField(material, "id", id);
		return material;
	}

	private static WeeklyMaterialOutboxSnapshot snapshot(WeeklyMaterialNotificationOutbox outbox) {
		return new WeeklyMaterialOutboxSnapshot(1L, outbox.weekStartDate(),
			outbox.materialType(), outbox.processedAt());
	}
}
