package com.faithlog.prayer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
	name = "prayer_weeks",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_prayer_weeks_campus_season_week", columnNames = {"campus_id", "season_id", "week_start_date"})
	}
)
public class PrayerWeek {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "season_id", nullable = false)
	private Long seasonId;

	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Column(name = "week_end_date", nullable = false)
	private LocalDate weekEndDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PrayerWeekStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PrayerWeek() {
	}

	private PrayerWeek(Long campusId, Long seasonId, LocalDate weekStartDate) {
		this.campusId = campusId;
		this.seasonId = seasonId;
		this.weekStartDate = weekStartDate;
		this.weekEndDate = weekStartDate.plusDays(6);
		this.status = PrayerWeekStatus.OPEN;
	}

	public static PrayerWeek create(Long campusId, Long seasonId, LocalDate weekStartDate) {
		return new PrayerWeek(campusId, seasonId, weekStartDate);
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

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public Long seasonId() {
		return seasonId;
	}

	public LocalDate weekStartDate() {
		return weekStartDate;
	}

	public LocalDate weekEndDate() {
		return weekEndDate;
	}

	public PrayerWeekStatus status() {
		return status;
	}
}
