package com.faithlog.prayer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "prayer_groups")
public class PrayerGroup {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "season_id", nullable = false)
	private Long seasonId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PrayerGroup() {
	}

	private PrayerGroup(Long seasonId, String name, int sortOrder) {
		this.seasonId = seasonId;
		this.name = name;
		this.sortOrder = sortOrder;
		this.isActive = true;
	}

	public static PrayerGroup create(Long seasonId, String name, int sortOrder) {
		return new PrayerGroup(seasonId, name, sortOrder);
	}

	public void update(String name, Integer sortOrder, Boolean isActive) {
		if (name != null) {
			this.name = name;
		}
		if (sortOrder != null) {
			this.sortOrder = sortOrder;
		}
		if (isActive != null) {
			this.isActive = isActive;
		}
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

	public Long seasonId() {
		return seasonId;
	}

	public String name() {
		return name;
	}

	public int sortOrder() {
		return sortOrder;
	}

	public boolean isActive() {
		return isActive;
	}
}
