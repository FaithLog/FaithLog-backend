package com.faithlog.user.service.result;

import java.time.Instant;
import java.time.OffsetDateTime;

public record YearlyRecapPresentationResult(
	boolean shouldAutoPresent,
	boolean homeCardVisible,
	OffsetDateTime homeCardVisibleUntil,
	Instant firstPresentedAt
) {
}
