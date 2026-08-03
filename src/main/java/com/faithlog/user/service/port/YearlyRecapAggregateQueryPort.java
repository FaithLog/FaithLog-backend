package com.faithlog.user.service.port;

import java.time.LocalDate;
import java.util.List;

public interface YearlyRecapAggregateQueryPort {

	boolean isCoverageComplete(int recapYear);

	List<CampusRecapActivity> findActiveCampuses(Long userId, LocalDate endDateExclusive);

	DevotionRecapSource findDevotion(Long userId, LocalDate startDate, LocalDate endDateExclusive);

	PrayerRecapAggregate findPrayer(Long userId, LocalDate startDate, LocalDate endDateExclusive);

	CommentActivityRecapAggregate findCommentActivity(
		Long userId,
		LocalDate startDate,
		LocalDate endDateExclusive
	);

	PenaltySummaryRecapAggregate findPenaltySummary(
		Long userId,
		LocalDate startDate,
		LocalDate endDateExclusive
	);
}
