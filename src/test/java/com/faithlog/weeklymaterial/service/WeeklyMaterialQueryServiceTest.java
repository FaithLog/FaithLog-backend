package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialAccessPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialQueryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class WeeklyMaterialQueryServiceTest {
	private final WeeklyMaterialQueryPort queries = mock(WeeklyMaterialQueryPort.class);
	private final WeeklyMaterialAccessPort access = mock(WeeklyMaterialAccessPort.class);
	private final Clock clock = Clock.fixed(Instant.parse("2026-08-02T15:30:00Z"), ZoneOffset.UTC);
	private final WeeklyMaterialQueryService service = new WeeklyMaterialQueryService(queries, access, clock);

	@Test
	void weekResponseKeepsGuideAndSheetAsIndependentNullableSlots() {
		LocalDate week = LocalDate.of(2026, 8, 3);
		when(queries.findActiveRows(1L, List.of(week))).thenReturn(List.of(
			new WeeklyMaterialRow(2L, week, WeeklyMaterialType.SHARING_SHEET, 20L,
				"sheet.pdf", 200L, "b".repeat(64), Instant.parse("2026-08-03T02:00:00Z"))));

		var result = service.getCurrent(1L, 100L);

		assertThat(result.weekStartDate()).isEqualTo(week);
		assertThat(result.shepherdGuide()).isNull();
		assertThat(result.sharingSheet().assetId()).isEqualTo(20L);
	}

	@Test
	void yearlyListPagesDistinctWeeksDescendingAndLoadsBothSlots() {
		LocalDate newer = LocalDate.of(2026, 8, 3);
		LocalDate older = LocalDate.of(2026, 7, 27);
		PageRequest pageable = PageRequest.of(0, 2);
		when(queries.findActiveWeekDates(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), pageable))
			.thenReturn(new PageImpl<>(List.of(newer, older), pageable, 2));
		when(queries.findActiveRows(1L, List.of(newer, older))).thenReturn(List.of(
			new WeeklyMaterialRow(3L, newer, WeeklyMaterialType.SHEPHERD_GUIDE, 30L,
				"guide.pdf", 300L, "c".repeat(64), Instant.parse("2026-08-03T03:00:00Z")),
			new WeeklyMaterialRow(2L, newer, WeeklyMaterialType.SHARING_SHEET, 20L,
				"sheet.pdf", 200L, "b".repeat(64), Instant.parse("2026-08-03T02:00:00Z")),
			new WeeklyMaterialRow(1L, older, WeeklyMaterialType.SHEPHERD_GUIDE, 10L,
				"old.pdf", 100L, "a".repeat(64), Instant.parse("2026-07-27T01:00:00Z"))));

		var result = service.list(1L, 100L, 2026, 0, 2);

		assertThat(result.getContent()).extracting(item -> item.weekStartDate())
			.containsExactly(newer, older);
		assertThat(result.getContent().getFirst().shepherdGuide().assetId()).isEqualTo(30L);
		assertThat(result.getContent().getFirst().sharingSheet().assetId()).isEqualTo(20L);
	}
}
