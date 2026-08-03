package com.faithlog.user.controller.dto.response;

import com.faithlog.user.service.result.CampusJourneyResult;
import com.faithlog.user.service.result.DevotionRecapResult;
import com.faithlog.user.service.result.PollActivityRecapResult;
import com.faithlog.user.service.result.PrayerActivityRecapResult;
import com.faithlog.user.service.result.YearlyRecapPresentationResult;
import com.faithlog.user.service.result.YearlyRecapResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record YearlyRecapResponse(
	int recapYear,
	boolean hasRecapData,
	PresentationResponse presentation,
	CampusJourneyResponse campusJourney,
	DevotionResponse devotion,
	PrayerActivityResponse prayerActivity,
	PollActivityResponse pollActivity
) {
	public static YearlyRecapResponse from(YearlyRecapResult result) {
		return new YearlyRecapResponse(
			result.recapYear(),
			result.hasRecapData(),
			PresentationResponse.from(result.presentation()),
			new CampusJourneyResponse(result.campuses().stream().map(CampusResponse::from).toList()),
			DevotionResponse.from(result.devotion()),
			PrayerActivityResponse.from(result.prayerActivity()),
			PollActivityResponse.from(result.pollActivity())
		);
	}

	public record PresentationResponse(
		boolean shouldAutoPresent,
		boolean homeCardVisible,
		OffsetDateTime homeCardVisibleUntil,
		Instant firstPresentedAt
	) {
		static PresentationResponse from(YearlyRecapPresentationResult result) {
			return new PresentationResponse(
				result.shouldAutoPresent(),
				result.homeCardVisible(),
				result.homeCardVisibleUntil(),
				result.firstPresentedAt()
			);
		}
	}

	public record CampusJourneyResponse(List<CampusResponse> campuses) {
		public CampusJourneyResponse {
			campuses = List.copyOf(campuses);
		}
	}

	public record CampusResponse(
		Long campusId,
		String campusName,
		LocalDate joinedDate,
		boolean joinedDuringRecapYear
	) {
		static CampusResponse from(CampusJourneyResult result) {
			return new CampusResponse(
				result.campusId(),
				result.campusName(),
				result.joinedDate(),
				result.joinedDuringRecapYear()
			);
		}
	}

	public record DevotionResponse(
		int quietTimeCount,
		int bibleReadingCount,
		int prayerCount,
		int allCompletedDayCount,
		int submittedWeekCount,
		int longestStreakDays,
		Integer mostActiveMonth
	) {
		static DevotionResponse from(DevotionRecapResult result) {
			return new DevotionResponse(
				result.quietTimeCount(), result.bibleReadingCount(), result.prayerCount(),
				result.allCompletedDayCount(), result.submittedWeekCount(),
				result.longestStreakDays(), result.mostActiveMonth()
			);
		}
	}

	public record PrayerActivityResponse(int submittedWeekCount, int participatedSeasonCount) {
		static PrayerActivityResponse from(PrayerActivityRecapResult result) {
			return new PrayerActivityResponse(result.submittedWeekCount(), result.participatedSeasonCount());
		}
	}

	public record PollActivityResponse(
		int participatedCount,
		int wedServicePollCount,
		int saturdayLeaderPollCount,
		int coffeePollCount,
		int mealPollCount,
		int customPollCount,
		int commentCount
	) {
		static PollActivityResponse from(PollActivityRecapResult result) {
			return new PollActivityResponse(
				result.participatedCount(), result.wedServicePollCount(),
				result.saturdayLeaderPollCount(), result.coffeePollCount(),
				result.mealPollCount(), result.customPollCount(), result.commentCount()
			);
		}
	}
}
