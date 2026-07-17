package com.faithlog.billing.service.policy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class ChargeArchivePolicy {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private ChargeArchivePolicy() {
	}

	public static Instant terminalCompletedAtFrom(Clock clock, boolean includeArchived) {
		if (includeArchived) {
			return null;
		}
		return ZonedDateTime.now(clock.withZone(SEOUL)).minusMonths(1).toInstant();
	}
}
