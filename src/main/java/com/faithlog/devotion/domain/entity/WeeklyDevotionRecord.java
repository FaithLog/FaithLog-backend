package com.faithlog.devotion.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Entity
@Table(
	name = "weekly_devotion_records",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_weekly_devotion_records_campus_user_week",
		columnNames = {"campus_id", "user_id", "week_start_date"}
	)
)
public class WeeklyDevotionRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Column(name = "week_end_date", nullable = false)
	private LocalDate weekEndDate;

	@Column(name = "quiet_time_count", nullable = false)
	private int quietTimeCount;

	@Column(name = "prayer_count", nullable = false)
	private int prayerCount;

	@Column(name = "bible_reading_count", nullable = false)
	private int bibleReadingCount;

	@Column(name = "saturday_late_minutes", nullable = false)
	private int saturdayLateMinutes;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected WeeklyDevotionRecord() {
	}

	private WeeklyDevotionRecord(Long campusId, Long userId, LocalDate weekStartDate) {
		this.campusId = campusId;
		this.userId = userId;
		this.weekStartDate = weekStartDate;
		this.weekEndDate = weekStartDate.plusDays(6);
		this.quietTimeCount = 0;
		this.prayerCount = 0;
		this.bibleReadingCount = 0;
		this.saturdayLateMinutes = 0;
	}

	public static WeeklyDevotionRecord create(Long campusId, Long userId, LocalDate weekStartDate) {
		return new WeeklyDevotionRecord(campusId, userId, weekStartDate);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public void updateSummary(List<DevotionDailyCheck> dailyChecks, int saturdayLateMinutes) {
		this.quietTimeCount = (int) dailyChecks.stream().filter(DevotionDailyCheck::quietTimeChecked).count();
		this.prayerCount = (int) dailyChecks.stream().filter(DevotionDailyCheck::prayerChecked).count();
		this.bibleReadingCount = (int) dailyChecks.stream().filter(DevotionDailyCheck::bibleReadingChecked).count();
		this.saturdayLateMinutes = saturdayLateMinutes;
	}

	public void submit(Instant submittedAt) {
		this.submittedAt = submittedAt;
	}

	public void reopenForResubmission() {
		this.submittedAt = null;
	}

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public Long userId() {
		return userId;
	}

	public LocalDate weekStartDate() {
		return weekStartDate;
	}

	public LocalDate weekEndDate() {
		return weekEndDate;
	}

	public int quietTimeCount() {
		return quietTimeCount;
	}

	public int prayerCount() {
		return prayerCount;
	}

	public int bibleReadingCount() {
		return bibleReadingCount;
	}

	public int saturdayLateMinutes() {
		return saturdayLateMinutes;
	}

	public Instant submittedAt() {
		return submittedAt;
	}
}
