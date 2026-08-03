package com.faithlog.user.service;

import com.faithlog.poll.domain.type.PollType;
import com.faithlog.user.service.port.CampusRecapActivity;
import com.faithlog.user.service.port.DevotionDailyActivity;
import com.faithlog.user.service.port.DevotionRecapSource;
import com.faithlog.user.service.port.PollRecapAggregate;
import com.faithlog.user.service.port.PrayerRecapAggregate;
import com.faithlog.user.service.result.CampusJourneyResult;
import com.faithlog.user.service.result.DevotionRecapResult;
import com.faithlog.user.service.result.PollActivityRecapResult;
import com.faithlog.user.service.result.PrayerActivityRecapResult;
import com.faithlog.user.service.result.YearlyRecapSnapshotData;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class YearlyRecapSnapshotAssembler {

	public YearlyRecapSnapshotData assemble(
		int recapYear,
		List<CampusRecapActivity> campusActivities,
		DevotionRecapSource devotionSource,
		PrayerRecapAggregate prayerAggregate,
		PollRecapAggregate pollAggregate
	) {
		List<CampusJourneyResult> campuses = campusActivities.stream()
			.sorted(Comparator.comparing(CampusRecapActivity::campusId))
			.map(campus -> new CampusJourneyResult(
				campus.campusId(),
				campus.campusName(),
				campus.joinedDate(),
				campus.joinedDate().getYear() == recapYear
			))
			.toList();
		DevotionRecapResult devotion = assembleDevotion(devotionSource);
		PrayerActivityRecapResult prayer = new PrayerActivityRecapResult(
			prayerAggregate.submittedWeekCount(),
			prayerAggregate.participatedSeasonCount()
		);
		PollActivityRecapResult poll = new PollActivityRecapResult(
			pollAggregate.participatedCount(),
			pollAggregate.count(PollType.WED_SERVICE),
			pollAggregate.count(PollType.SATURDAY_LEADER),
			pollAggregate.count(PollType.COFFEE),
			pollAggregate.count(PollType.MEAL),
			pollAggregate.count(PollType.CUSTOM),
			pollAggregate.commentCount()
		);
		boolean hasRecapData = !campuses.isEmpty()
			|| hasDevotionData(devotion)
			|| prayer.submittedWeekCount() > 0
			|| prayer.participatedSeasonCount() > 0
			|| poll.participatedCount() > 0
			|| poll.commentCount() > 0;

		return new YearlyRecapSnapshotData(recapYear, hasRecapData, campuses, devotion, prayer, poll);
	}

	private DevotionRecapResult assembleDevotion(DevotionRecapSource source) {
		List<DevotionDailyActivity> activities = mergeDailyActivities(source.dailyActivities());
		int quietTimeCount = 0;
		int bibleReadingCount = 0;
		int prayerCount = 0;
		int allCompletedDayCount = 0;
		int currentStreak = 0;
		int longestStreak = 0;
		LocalDate previousCompletedDate = null;
		int[] activityByMonth = new int[13];

		for (DevotionDailyActivity activity : activities) {
			quietTimeCount += activity.quietTimeChecked() ? 1 : 0;
			bibleReadingCount += activity.bibleReadingChecked() ? 1 : 0;
			prayerCount += activity.prayerChecked() ? 1 : 0;
			activityByMonth[activity.recordDate().getMonthValue()] += activity.completedCount();
			if (activity.allCompleted()) {
				allCompletedDayCount++;
				currentStreak = previousCompletedDate != null
					&& activity.recordDate().equals(previousCompletedDate.plusDays(1))
					? currentStreak + 1
					: 1;
				longestStreak = Math.max(longestStreak, currentStreak);
				previousCompletedDate = activity.recordDate();
			}
		}

		return new DevotionRecapResult(
			quietTimeCount,
			bibleReadingCount,
			prayerCount,
			allCompletedDayCount,
			source.submittedWeekCount(),
			longestStreak,
			mostActiveMonth(activityByMonth)
		);
	}

	private List<DevotionDailyActivity> mergeDailyActivities(List<DevotionDailyActivity> source) {
		TreeMap<LocalDate, DevotionDailyActivity> byDate = new TreeMap<>();
		for (DevotionDailyActivity activity : source) {
			byDate.merge(activity.recordDate(), activity, (left, right) -> new DevotionDailyActivity(
				left.recordDate(),
				left.quietTimeChecked() || right.quietTimeChecked(),
				left.bibleReadingChecked() || right.bibleReadingChecked(),
				left.prayerChecked() || right.prayerChecked()
			));
		}
		return List.copyOf(byDate.values());
	}

	private Integer mostActiveMonth(int[] activityByMonth) {
		int selectedMonth = 0;
		int selectedCount = 0;
		for (int month = 1; month <= 12; month++) {
			if (activityByMonth[month] > selectedCount) {
				selectedMonth = month;
				selectedCount = activityByMonth[month];
			}
		}
		return selectedMonth == 0 ? null : selectedMonth;
	}

	private boolean hasDevotionData(DevotionRecapResult devotion) {
		return devotion.quietTimeCount() > 0
			|| devotion.bibleReadingCount() > 0
			|| devotion.prayerCount() > 0
			|| devotion.submittedWeekCount() > 0;
	}
}
