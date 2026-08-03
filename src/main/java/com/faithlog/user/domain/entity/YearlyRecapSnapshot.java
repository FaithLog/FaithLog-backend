package com.faithlog.user.domain.entity;

import com.faithlog.user.service.result.DevotionRecapResult;
import com.faithlog.user.service.result.PollActivityRecapResult;
import com.faithlog.user.service.result.PrayerActivityRecapResult;
import com.faithlog.user.service.result.YearlyRecapSnapshotData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.List;

@Entity
@Table(
	name = "yearly_recap_snapshots",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_yearly_recap_snapshots_user_year",
		columnNames = {"user_id", "recap_year"}
	)
)
public class YearlyRecapSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "user_id", nullable = false)
	private Long userId;
	@Column(name = "recap_year", nullable = false)
	private int recapYear;
	@Column(name = "has_recap_data", nullable = false)
	private boolean hasRecapData;
	@Column(name = "devotion_quiet_time_count", nullable = false)
	private int devotionQuietTimeCount;
	@Column(name = "devotion_bible_reading_count", nullable = false)
	private int devotionBibleReadingCount;
	@Column(name = "devotion_prayer_count", nullable = false)
	private int devotionPrayerCount;
	@Column(name = "devotion_all_completed_day_count", nullable = false)
	private int devotionAllCompletedDayCount;
	@Column(name = "devotion_submitted_week_count", nullable = false)
	private int devotionSubmittedWeekCount;
	@Column(name = "devotion_longest_streak_days", nullable = false)
	private int devotionLongestStreakDays;
	@Column(name = "devotion_most_active_month")
	private Integer devotionMostActiveMonth;
	@Column(name = "prayer_submitted_week_count", nullable = false)
	private int prayerSubmittedWeekCount;
	@Column(name = "prayer_participated_season_count", nullable = false)
	private int prayerParticipatedSeasonCount;
	@Column(name = "poll_participated_count", nullable = false)
	private int pollParticipatedCount;
	@Column(name = "poll_wed_service_count", nullable = false)
	private int pollWedServiceCount;
	@Column(name = "poll_saturday_leader_count", nullable = false)
	private int pollSaturdayLeaderCount;
	@Column(name = "poll_coffee_count", nullable = false)
	private int pollCoffeeCount;
	@Column(name = "poll_meal_count", nullable = false)
	private int pollMealCount;
	@Column(name = "poll_custom_count", nullable = false)
	private int pollCustomCount;
	@Column(name = "poll_comment_count", nullable = false)
	private int pollCommentCount;
	@Column(name = "first_presented_at")
	private Instant firstPresentedAt;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected YearlyRecapSnapshot() {
	}

	private YearlyRecapSnapshot(Long userId, YearlyRecapSnapshotData data, Instant now) {
		this.userId = userId;
		this.recapYear = data.recapYear();
		this.hasRecapData = data.hasRecapData();
		DevotionRecapResult devotion = data.devotion();
		this.devotionQuietTimeCount = devotion.quietTimeCount();
		this.devotionBibleReadingCount = devotion.bibleReadingCount();
		this.devotionPrayerCount = devotion.prayerCount();
		this.devotionAllCompletedDayCount = devotion.allCompletedDayCount();
		this.devotionSubmittedWeekCount = devotion.submittedWeekCount();
		this.devotionLongestStreakDays = devotion.longestStreakDays();
		this.devotionMostActiveMonth = devotion.mostActiveMonth();
		PrayerActivityRecapResult prayer = data.prayerActivity();
		this.prayerSubmittedWeekCount = prayer.submittedWeekCount();
		this.prayerParticipatedSeasonCount = prayer.participatedSeasonCount();
		PollActivityRecapResult poll = data.pollActivity();
		this.pollParticipatedCount = poll.participatedCount();
		this.pollWedServiceCount = poll.wedServicePollCount();
		this.pollSaturdayLeaderCount = poll.saturdayLeaderPollCount();
		this.pollCoffeeCount = poll.coffeePollCount();
		this.pollMealCount = poll.mealPollCount();
		this.pollCustomCount = poll.customPollCount();
		this.pollCommentCount = poll.commentCount();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static YearlyRecapSnapshot create(Long userId, YearlyRecapSnapshotData data, Instant now) {
		return new YearlyRecapSnapshot(userId, data, now);
	}

	public void markPresented(Instant now) {
		if (firstPresentedAt == null && hasRecapData) {
			firstPresentedAt = now;
			updatedAt = now;
		}
	}

	public YearlyRecapSnapshotData toData(List<YearlyRecapCampusSnapshot> campuses) {
		return new YearlyRecapSnapshotData(
			recapYear,
			hasRecapData,
			campuses.stream().map(YearlyRecapCampusSnapshot::toResult).toList(),
			new DevotionRecapResult(
				devotionQuietTimeCount, devotionBibleReadingCount, devotionPrayerCount,
				devotionAllCompletedDayCount, devotionSubmittedWeekCount,
				devotionLongestStreakDays, devotionMostActiveMonth
			),
			new PrayerActivityRecapResult(prayerSubmittedWeekCount, prayerParticipatedSeasonCount),
			new PollActivityRecapResult(
				pollParticipatedCount, pollWedServiceCount, pollSaturdayLeaderCount,
				pollCoffeeCount, pollMealCount, pollCustomCount, pollCommentCount
			)
		);
	}

	public Long id() {
		return id;
	}

	public boolean hasRecapData() {
		return hasRecapData;
	}

	public Instant firstPresentedAt() {
		return firstPresentedAt;
	}
}
