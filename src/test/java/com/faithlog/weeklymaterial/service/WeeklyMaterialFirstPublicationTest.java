package com.faithlog.weeklymaterial.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WeeklyMaterialFirstPublicationTest {
	private final WeeklyMaterialNotificationOutboxRepositoryPort outboxes =
		mock(WeeklyMaterialNotificationOutboxRepositoryPort.class);
	private final WeeklyMaterialFirstPublication service = new WeeklyMaterialFirstPublication(outboxes);

	@Test
	void firstSharingSheetCreatesOneDurableOutboxButGuideAndReregistrationCreateNone() {
		LocalDate week = LocalDate.of(2026, 8, 3);
		WeeklyMaterial sheet = WeeklyMaterial.create(1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, 20L, 100L);
		org.springframework.test.util.ReflectionTestUtils.setField(sheet, "id", 10L);
		WeeklyMaterial guide = WeeklyMaterial.create(1L, week, WeeklyMaterialType.SHEPHERD_GUIDE, 21L, 100L);
		WeeklyMaterial saturday = WeeklyMaterial.create(
			1L, week, WeeklyMaterialType.SATURDAY_LEADER_SHARING_SHEET, 22L, 100L);
		when(outboxes.findSlotForUpdate(week, WeeklyMaterialType.SUNDAY_SHARING_SHEET))
			.thenReturn(Optional.empty());

		service.recordFirstRegistration(sheet, 1L, true);
		service.recordFirstRegistration(guide, 1L, true);
		service.recordFirstRegistration(saturday, 1L, true);
		service.recordFirstRegistration(sheet, 1L, false);

		verify(outboxes).save(org.mockito.ArgumentMatchers.argThat(outbox ->
			outbox.publisherCampusId().equals(1L) && outbox.weeklyMaterialId().equals(10L)
				&& outbox.weekStartDate().equals(week) && outbox.uploaderId().equals(100L)));
		verify(outboxes, never()).save(org.mockito.ArgumentMatchers.argThat(outbox ->
			outbox.weeklyMaterialId() == null));
	}

	@Test
	void preservedPendingHistoryAfterPhysicalDeleteSuppressesReregistrationOutbox() {
		LocalDate week = LocalDate.of(2026, 5, 4);
		WeeklyMaterial reregistered = WeeklyMaterial.create(
			1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, 30L, 101L);
		org.springframework.test.util.ReflectionTestUtils.setField(reregistered, "id", 11L);
		WeeklyMaterialNotificationOutbox history =
			WeeklyMaterialNotificationOutbox.create(1L, 10L, week, 100L);
		when(outboxes.findSlotForUpdate(week, WeeklyMaterialType.SUNDAY_SHARING_SHEET))
			.thenReturn(Optional.of(history));

		service.recordFirstRegistration(reregistered, 2L, true);

		verify(outboxes, never()).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void preservedProcessedHistoryAfterPhysicalDeleteAlsoSuppressesReregistrationOutbox() {
		LocalDate week = LocalDate.of(2026, 5, 4);
		WeeklyMaterial reregistered = WeeklyMaterial.create(
			1L, week, WeeklyMaterialType.SUNDAY_SHARING_SHEET, 30L, 101L);
		org.springframework.test.util.ReflectionTestUtils.setField(reregistered, "id", 11L);
		WeeklyMaterialNotificationOutbox history =
			WeeklyMaterialNotificationOutbox.create(1L, 10L, week, 100L);
		history.markProcessed(java.time.Instant.parse("2026-05-04T00:00:00Z"));
		when(outboxes.findSlotForUpdate(week, WeeklyMaterialType.SUNDAY_SHARING_SHEET))
			.thenReturn(Optional.of(history));

		service.recordFirstRegistration(reregistered, 2L, true);

		verify(outboxes, never()).save(org.mockito.ArgumentMatchers.any());
	}
}
