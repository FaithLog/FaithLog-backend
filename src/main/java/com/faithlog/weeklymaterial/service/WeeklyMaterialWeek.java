package com.faithlog.weeklymaterial.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

public final class WeeklyMaterialWeek {
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private WeeklyMaterialWeek() {
	}

	public static LocalDate requireMonday(LocalDate date) {
		if (date == null || date.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new IllegalArgumentException("weekStartDate must be Monday");
		}
		return date;
	}

	public static LocalDate currentMonday(Clock clock) {
		return LocalDate.now(clock.withZone(SEOUL)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}
}
