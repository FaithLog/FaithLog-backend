package com.faithlog.user.service.policy;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class YearlyRecapPolicy {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final Clock clock;

	public YearlyRecapPolicy(Clock clock) {
		this.clock = clock;
	}

	public YearlyRecapPeriod previousPeriod() {
		ZonedDateTime now = clock.instant().atZone(SEOUL);
		int recapYear = now.getYear() - 1;
		LocalDate yearStart = LocalDate.of(recapYear, Month.JANUARY, 1);
		LocalDate yearEndExclusive = yearStart.plusYears(1);
		ZonedDateTime homeCardEndExclusive = ZonedDateTime.of(
			LocalDate.of(now.getYear(), Month.JANUARY, 15),
			LocalTime.MIDNIGHT,
			SEOUL
		);
		OffsetDateTime homeCardVisibleUntil = homeCardEndExclusive.minusSeconds(1).toOffsetDateTime();
		boolean homeCardVisible = now.isBefore(homeCardEndExclusive);

		return new YearlyRecapPeriod(
			recapYear,
			yearStart,
			yearEndExclusive,
			homeCardVisible,
			homeCardVisibleUntil
		);
	}

	public boolean isCurrentPreviousYear(int recapYear) {
		return previousPeriod().recapYear() == recapYear;
	}
}
