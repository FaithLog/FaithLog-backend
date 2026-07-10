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

@Entity
@Table(
	name = "devotion_daily_checks",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_devotion_daily_checks_weekly_record_date",
		columnNames = {"weekly_record_id", "record_date"}
	)
)
public class DevotionDailyCheck {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "weekly_record_id", nullable = false)
	private Long weeklyRecordId;

	@Column(name = "record_date", nullable = false)
	private LocalDate recordDate;

	@Column(name = "quiet_time_checked", nullable = false)
	private boolean quietTimeChecked;

	@Column(name = "prayer_checked", nullable = false)
	private boolean prayerChecked;

	@Column(name = "bible_reading_checked", nullable = false)
	private boolean bibleReadingChecked;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected DevotionDailyCheck() {
	}

	private DevotionDailyCheck(
		Long weeklyRecordId,
		LocalDate recordDate,
		boolean quietTimeChecked,
		boolean prayerChecked,
		boolean bibleReadingChecked
	) {
		this.weeklyRecordId = weeklyRecordId;
		this.recordDate = recordDate;
		this.quietTimeChecked = quietTimeChecked;
		this.prayerChecked = prayerChecked;
		this.bibleReadingChecked = bibleReadingChecked;
	}

	public static DevotionDailyCheck create(
		Long weeklyRecordId,
		LocalDate recordDate,
		boolean quietTimeChecked,
		boolean prayerChecked,
		boolean bibleReadingChecked
	) {
		return new DevotionDailyCheck(weeklyRecordId, recordDate, quietTimeChecked, prayerChecked, bibleReadingChecked);
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

	public void update(boolean quietTimeChecked, boolean prayerChecked, boolean bibleReadingChecked) {
		this.quietTimeChecked = quietTimeChecked;
		this.prayerChecked = prayerChecked;
		this.bibleReadingChecked = bibleReadingChecked;
	}

	public Long id() {
		return id;
	}

	public Long weeklyRecordId() {
		return weeklyRecordId;
	}

	public LocalDate recordDate() {
		return recordDate;
	}

	public boolean quietTimeChecked() {
		return quietTimeChecked;
	}

	public boolean prayerChecked() {
		return prayerChecked;
	}

	public boolean bibleReadingChecked() {
		return bibleReadingChecked;
	}
}
