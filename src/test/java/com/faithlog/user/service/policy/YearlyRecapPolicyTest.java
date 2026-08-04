package com.faithlog.user.service.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class YearlyRecapPolicyTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Test
	void calculates_the_previous_year_from_the_seoul_calendar() {
		YearlyRecapPeriod period = policyAt("2026-12-31T14:59:59Z").previousPeriod();

		assertThat(period.recapYear()).isEqualTo(2025);
		assertThat(period.yearStart()).isEqualTo(LocalDate.of(2025, 1, 1));
		assertThat(period.yearEndExclusive()).isEqualTo(LocalDate.of(2026, 1, 1));
	}

	@Test
	void changes_the_recap_year_exactly_at_new_year_in_seoul() {
		YearlyRecapPeriod before = policyAt("2026-12-31T14:59:59Z").previousPeriod();
		YearlyRecapPeriod after = policyAt("2026-12-31T15:00:00Z").previousPeriod();

		assertThat(before.recapYear()).isEqualTo(2025);
		assertThat(after.recapYear()).isEqualTo(2026);
	}

	@Test
	void exposes_the_home_card_through_the_end_of_january_fourteenth() {
		YearlyRecapPeriod beforeWindow = policyAt("2026-12-31T14:59:59Z").previousPeriod();
		YearlyRecapPeriod firstSecond = policyAt("2026-12-31T15:00:00Z").previousPeriod();
		YearlyRecapPeriod lastSecond = policyAt("2027-01-14T14:59:59Z").previousPeriod();
		YearlyRecapPeriod firstSecondAfter = policyAt("2027-01-14T15:00:00Z").previousPeriod();

		assertThat(beforeWindow.homeCardVisible()).isFalse();
		assertThat(firstSecond.homeCardVisible()).isTrue();
		assertThat(lastSecond.homeCardVisible()).isTrue();
		assertThat(lastSecond.homeCardVisibleUntil())
			.isEqualTo(OffsetDateTime.parse("2027-01-14T23:59:59+09:00"));
		assertThat(firstSecondAfter.homeCardVisible()).isFalse();
	}

	@Test
	void retains_the_exact_leap_year_boundaries() {
		YearlyRecapPeriod period = policyAt("2025-01-01T00:00:00Z").previousPeriod();

		assertThat(period.recapYear()).isEqualTo(2024);
		assertThat(period.yearStart()).isEqualTo(LocalDate.of(2024, 1, 1));
		assertThat(period.yearEndExclusive()).isEqualTo(LocalDate.of(2025, 1, 1));
		assertThat(period.yearStart().lengthOfYear()).isEqualTo(366);
	}

	@Test
	void accepts_only_the_current_previous_year_for_presented_requests() {
		YearlyRecapPolicy policy = policyAt("2027-08-03T00:00:00Z");

		assertThat(policy.isCurrentPreviousYear(2026)).isTrue();
		assertThat(policy.isCurrentPreviousYear(2025)).isFalse();
		assertThat(policy.isCurrentPreviousYear(2027)).isFalse();
	}

	private YearlyRecapPolicy policyAt(String instant) {
		return new YearlyRecapPolicy(Clock.fixed(Instant.parse(instant), SEOUL));
	}
}
