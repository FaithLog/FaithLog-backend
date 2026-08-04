package com.faithlog.announcement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "announcement_notification_outbox",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_announcement_notification_outbox_announcement",
		columnNames = "announcement_id"
	)
)
public class AnnouncementNotificationOutbox {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "announcement_id", nullable = false)
	private Long announcementId;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "category_id", nullable = false)
	private Long categoryId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(name = "category_name", nullable = false, length = 30)
	private String categoryName;

	@Column(name = "announcement_title", nullable = false, length = 100)
	private String announcementTitle;

	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AnnouncementNotificationOutbox() {
	}

	private AnnouncementNotificationOutbox(
		Long announcementId,
		Long campusId,
		Long categoryId,
		Long authorId,
		String categoryName,
		String announcementTitle,
		Instant publishedAt
	) {
		this.announcementId = announcementId;
		this.campusId = campusId;
		this.categoryId = categoryId;
		this.authorId = authorId;
		this.categoryName = categoryName;
		this.announcementTitle = announcementTitle;
		this.publishedAt = publishedAt;
	}

	public static AnnouncementNotificationOutbox create(
		Long announcementId,
		Long campusId,
		Long categoryId,
		Long authorId,
		String categoryName,
		String announcementTitle,
		Instant publishedAt
	) {
		return new AnnouncementNotificationOutbox(
			announcementId, campusId, categoryId, authorId, categoryName, announcementTitle, publishedAt);
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public void markProcessed() {
		if (processedAt == null) {
			processedAt = Instant.now();
		}
	}

	public boolean isProcessed() {
		return processedAt != null;
	}

	public Long id() { return id; }
	public Long announcementId() { return announcementId; }
	public Long campusId() { return campusId; }
	public Long categoryId() { return categoryId; }
	public Long authorId() { return authorId; }
	public String categoryName() { return categoryName; }
	public String announcementTitle() { return announcementTitle; }
	public Instant publishedAt() { return publishedAt; }
	public Instant processedAt() { return processedAt; }
}
