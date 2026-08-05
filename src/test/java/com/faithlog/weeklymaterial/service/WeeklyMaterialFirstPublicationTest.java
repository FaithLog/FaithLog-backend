package com.faithlog.weeklymaterial.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class WeeklyMaterialFirstPublicationTest {
	private final WeeklyMaterialNotificationOutboxRepositoryPort outboxes =
		mock(WeeklyMaterialNotificationOutboxRepositoryPort.class);
	private final WeeklyMaterialFirstPublication service = new WeeklyMaterialFirstPublication(outboxes);

	@Test
	void firstSharingSheetCreatesOneDurableOutboxButGuideAndReregistrationCreateNone() {
		LocalDate week = LocalDate.of(2026, 8, 3);
		WeeklyMaterial sheet = WeeklyMaterial.create(1L, week, WeeklyMaterialType.SHARING_SHEET, 20L, 100L);
		org.springframework.test.util.ReflectionTestUtils.setField(sheet, "id", 10L);
		WeeklyMaterial guide = WeeklyMaterial.create(1L, week, WeeklyMaterialType.SHEPHERD_GUIDE, 21L, 100L);

		service.recordFirstRegistration(sheet, true);
		service.recordFirstRegistration(guide, true);
		service.recordFirstRegistration(sheet, false);

		verify(outboxes).save(org.mockito.ArgumentMatchers.argThat(outbox ->
			outbox.campusId().equals(1L) && outbox.weeklyMaterialId().equals(10L)
				&& outbox.weekStartDate().equals(week) && outbox.uploaderId().equals(100L)));
		verify(outboxes, never()).save(org.mockito.ArgumentMatchers.argThat(outbox ->
			outbox.weeklyMaterialId() == null));
	}
}
