package com.faithlog.prayer.domain.entity;

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

@Entity
@Table(
	name = "prayer_group_members",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_prayer_group_members_group_user", columnNames = {"group_id", "user_id"})
	}
)
public class PrayerGroupMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false)
	private Long groupId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "is_active", nullable = false)
	private boolean isActive;

	@Column(name = "joined_at", nullable = false)
	private Instant joinedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PrayerGroupMember() {
	}

	private PrayerGroupMember(Long groupId, Long userId) {
		this.groupId = groupId;
		this.userId = userId;
		this.isActive = true;
		this.joinedAt = Instant.now();
	}

	public static PrayerGroupMember create(Long groupId, Long userId) {
		return new PrayerGroupMember(groupId, userId);
	}

	public void reactivate() {
		this.isActive = true;
		this.joinedAt = Instant.now();
	}

	public void deactivate() {
		this.isActive = false;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.joinedAt == null) {
			this.joinedAt = now;
		}
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public Long id() {
		return id;
	}

	public Long groupId() {
		return groupId;
	}

	public Long userId() {
		return userId;
	}

	public boolean isActive() {
		return isActive;
	}
}
