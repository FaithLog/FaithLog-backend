package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WeeklyMaterialWeekTest {

	@Test
	void acceptsMondayAndRejectsEveryOtherDay() {
		assertThat(WeeklyMaterialWeek.requireMonday(LocalDate.of(2026, 8, 3)))
			.isEqualTo(LocalDate.of(2026, 8, 3));
		assertThatThrownBy(() -> WeeklyMaterialWeek.requireMonday(LocalDate.of(2026, 8, 4)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void currentWeekUsesAsiaSeoulAtUtcDateBoundary() {
		Clock clock = Clock.fixed(Instant.parse("2026-08-02T15:30:00Z"), ZoneId.of("UTC"));

		assertThat(WeeklyMaterialWeek.currentMonday(clock)).isEqualTo(LocalDate.of(2026, 8, 3));
	}
}
