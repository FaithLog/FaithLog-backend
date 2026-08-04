package com.faithlog.user.service.policy;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record YearlyRecapPeriod(
	int recapYear,
	LocalDate yearStart,
	LocalDate yearEndExclusive,
	boolean homeCardVisible,
	OffsetDateTime homeCardVisibleUntil
) {
}
