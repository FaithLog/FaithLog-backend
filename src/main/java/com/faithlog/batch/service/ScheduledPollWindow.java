package com.faithlog.batch.service;

import com.faithlog.poll.domain.entity.PollTemplate;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

record ScheduledPollWindow(
	LocalDate weekStartDate,
	Instant weekStartInstant,
	Instant nextWeekStartInstant,
	Instant startsAt,
	Instant endsAt
) {

	static ScheduledPollWindow from(PollTemplate template, Instant now) {
		LocalDate currentDate = now.atZone(PollAutomationService.SEOUL_ZONE).toLocalDate();
		LocalDate weekStartDate = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		Instant weekStartInstant = weekStartDate.atStartOfDay(PollAutomationService.SEOUL_ZONE).toInstant();
		Instant nextWeekStartInstant = weekStartDate.plusWeeks(1).atStartOfDay(PollAutomationService.SEOUL_ZONE).toInstant();
		Instant startsAt = scheduledInstant(weekStartDate, template.startDayOfWeek(), template.startTime());
		Instant endsAt = scheduledInstant(weekStartDate, template.endDayOfWeek(), template.endTime());
		if (!endsAt.isAfter(startsAt)) {
			endsAt = LocalDateTime.ofInstant(endsAt, PollAutomationService.SEOUL_ZONE)
				.plusWeeks(1)
				.atZone(PollAutomationService.SEOUL_ZONE)
				.toInstant();
		}
		return new ScheduledPollWindow(weekStartDate, weekStartInstant, nextWeekStartInstant, startsAt, endsAt);
	}

	private static Instant scheduledInstant(LocalDate weekStartDate, DayOfWeek dayOfWeek, java.time.LocalTime time) {
		return weekStartDate.plusDays(dayOfWeek.getValue() - DayOfWeek.MONDAY.getValue())
			.atTime(time)
			.atZone(PollAutomationService.SEOUL_ZONE)
			.toInstant();
	}

	boolean isDue(Instant now) {
		return !now.isBefore(startsAt) && now.isBefore(endsAt);
	}
}
