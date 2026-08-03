package com.faithlog.user.service.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface YearlyRecapAggregateQueryPort {

	List<CampusRecapActivity> findActiveCampuses(Long userId, LocalDate endDateExclusive);

	DevotionRecapSource findDevotion(Long userId, LocalDate startDate, LocalDate endDateExclusive);

	PrayerRecapAggregate findPrayer(Long userId, LocalDate startDate, LocalDate endDateExclusive);

	PollRecapAggregate findPoll(Long userId, Instant startInclusive, Instant endExclusive);
}
