package com.faithlog.announcement.domain.entity;

import com.faithlog.announcement.domain.type.AnnouncementStatus;
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

@Entity
@Table(name = "announcements")
public class Announcement {

	private static final int TITLE_MAX_LENGTH = 100;
	private static final int CONTENT_MAX_LENGTH = 5000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "category_id", nullable = false)
	private Long categoryId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(nullable = false, length = TITLE_MAX_LENGTH)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@Column(name = "is_pinned", nullable = false)
	private boolean pinned;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AnnouncementStatus status;

	@Column(name = "publish_at", nullable = false)
	private Instant publishAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Announcement() {
	}

	private Announcement(
		Long campusId,
		Long categoryId,
		Long authorId,
		String title,
		String content,
		boolean pinned,
		AnnouncementStatus status,
		Instant publishAt,
		Instant publishedAt
	) {
		this.campusId = requireId(campusId, "campusId");
		this.categoryId = requireId(categoryId, "categoryId");
		this.authorId = requireId(authorId, "authorId");
		this.title = normalizeText(title, TITLE_MAX_LENGTH, "title");
		this.content = normalizeText(content, CONTENT_MAX_LENGTH, "content");
		this.pinned = pinned;
		this.status = status;
		this.publishAt = publishAt;
		this.publishedAt = publishedAt;
	}

	public static Announcement createPublished(
		Long campusId,
		Long categoryId,
		Long authorId,
		String title,
		String content,
		boolean pinned,
		Instant now
	) {
		Instant publicationTime = requireInstant(now, "now");
		return new Announcement(
			campusId,
			categoryId,
			authorId,
			title,
			content,
			pinned,
			AnnouncementStatus.PUBLISHED,
			publicationTime,
			publicationTime
		);
	}

	public static Announcement createScheduled(
		Long campusId,
		Long categoryId,
		Long authorId,
		String title,
		String content,
		boolean pinned,
		Instant publishAt,
		Instant now
	) {
		Instant currentTime = requireInstant(now, "now");
		Instant scheduledTime = requireInstant(publishAt, "publishAt");
		if (!scheduledTime.isAfter(currentTime)) {
			throw new IllegalArgumentException("publishAt must be in the future");
		}
		return new Announcement(
			campusId,
			categoryId,
			authorId,
			title,
			content,
			pinned,
			AnnouncementStatus.SCHEDULED,
			scheduledTime,
			null
		);
	}

	public void update(
		Long categoryId,
		String title,
		String content,
		boolean pinned,
		Instant newPublishAt,
		Instant now
	) {
		if (status == AnnouncementStatus.ARCHIVED) {
			throw new IllegalStateException("archived announcement cannot be updated");
		}
		this.categoryId = requireId(categoryId, "categoryId");
		this.title = normalizeText(title, TITLE_MAX_LENGTH, "title");
		this.content = normalizeText(content, CONTENT_MAX_LENGTH, "content");
		this.pinned = pinned;
		if (status == AnnouncementStatus.SCHEDULED) {
			Instant currentTime = requireInstant(now, "now");
			Instant scheduledTime = requireInstant(newPublishAt, "publishAt");
			if (!scheduledTime.isAfter(currentTime)) {
				throw new IllegalArgumentException("publishAt must be in the future");
			}
			this.publishAt = scheduledTime;
		} else if (newPublishAt != null) {
			throw new IllegalStateException("publishedAt cannot be changed");
		}
	}

	public void publish(Instant now) {
		if (status != AnnouncementStatus.SCHEDULED) {
			throw new IllegalStateException("only scheduled announcement can be published");
		}
		Instant publicationTime = requireInstant(now, "now");
		this.status = AnnouncementStatus.PUBLISHED;
		this.publishAt = publicationTime;
		this.publishedAt = publicationTime;
	}

	public void archive() {
		if (status == AnnouncementStatus.ARCHIVED) {
			return;
		}
		this.status = AnnouncementStatus.ARCHIVED;
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

	private static Long requireId(Long value, String fieldName) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(fieldName + " must be positive");
		}
		return value;
	}

	private static Instant requireInstant(Instant value, String fieldName) {
		if (value == null) {
			throw new IllegalArgumentException(fieldName + " must not be null");
		}
		return value;
	}

	private static String normalizeText(String value, int maxLength, String fieldName) {
		if (value == null) {
			throw new IllegalArgumentException(fieldName + " must not be null");
		}
		String normalized = value.trim();
		if (normalized.isEmpty() || normalized.length() > maxLength) {
			throw new IllegalArgumentException(fieldName + " length is invalid");
		}
		return normalized;
	}

	public Long id() {
		return id;
	}

	public Long campusId() {
		return campusId;
	}

	public Long categoryId() {
		return categoryId;
	}

	public Long authorId() {
		return authorId;
	}

	public String title() {
		return title;
	}

	public String content() {
		return content;
	}

	public boolean isPinned() {
		return pinned;
	}

	public AnnouncementStatus status() {
		return status;
	}

	public Instant publishAt() {
		return publishAt;
	}

	public Instant publishedAt() {
		return publishedAt;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
