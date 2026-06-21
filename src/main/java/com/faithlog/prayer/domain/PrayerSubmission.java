package com.faithlog.prayer.domain;

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
	name = "prayer_submissions",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_prayer_submissions_week_user", columnNames = {"prayer_week_id", "user_id"})
	}
)
public class PrayerSubmission {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "prayer_week_id", nullable = false)
	private Long prayerWeekId;

	@Column(name = "group_id", nullable = false)
	private Long groupId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(columnDefinition = "text")
	private String content;

	@Column(name = "submitted_by")
	private Long submittedBy;

	@Column(name = "submitted_at")
	private Instant submittedAt;

	@Column(nullable = false)
	private int version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PrayerSubmission() {
	}

	private PrayerSubmission(Long prayerWeekId, Long groupId, Long userId, String content, Long submittedBy, Instant submittedAt) {
		this.prayerWeekId = prayerWeekId;
		this.groupId = groupId;
		this.userId = userId;
		this.content = content;
		this.submittedBy = submittedBy;
		this.submittedAt = submittedAt;
		this.version = 1;
	}

	public static PrayerSubmission create(Long prayerWeekId, Long groupId, Long userId, String content, Long submittedBy, Instant submittedAt) {
		return new PrayerSubmission(prayerWeekId, groupId, userId, content, submittedBy, submittedAt);
	}

	public void update(String content, Long submittedBy, Instant submittedAt) {
		this.content = content;
		this.submittedBy = submittedBy;
		this.submittedAt = submittedAt;
		this.version += 1;
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

	public Long prayerWeekId() {
		return prayerWeekId;
	}

	public Long groupId() {
		return groupId;
	}

	public Long userId() {
		return userId;
	}

	public String content() {
		return content;
	}

	public Instant submittedAt() {
		return submittedAt;
	}

	public int version() {
		return version;
	}
}
