package com.faithlog.prayer.domain.entity;

import com.faithlog.prayer.domain.type.PrayerSeasonStatus;
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
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "prayer_seasons")
public class PrayerSeason {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date")
	private LocalDate endDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PrayerSeasonStatus status;

	@Column(name = "created_by")
	private Long createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PrayerSeason() {
	}

	private PrayerSeason(Long campusId, String name, LocalDate startDate, Long createdBy) {
		this.campusId = campusId;
		this.name = name;
		this.startDate = startDate;
		this.status = PrayerSeasonStatus.ACTIVE;
		this.createdBy = createdBy;
	}

	public static PrayerSeason create(Long campusId, String name, LocalDate startDate, Long createdBy) {
		return new PrayerSeason(campusId, name, startDate, createdBy);
	}

	public void close(LocalDate endDate) {
		this.endDate = endDate;
		this.status = PrayerSeasonStatus.CLOSED;
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

	public String name() {
		return name;
	}

	public LocalDate startDate() {
		return startDate;
	}

	public LocalDate endDate() {
		return endDate;
	}

	public PrayerSeasonStatus status() {
		return status;
	}
}
