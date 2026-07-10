package com.faithlog.poll.domain.entity;

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
@Table(name = "poll_comments")
public class PollComment {

	public static final String DELETED_CONTENT = "삭제된 댓글입니다.";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "poll_id", nullable = false)
	private Long pollId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 1000)
	private String content;

	@Column(name = "is_deleted", nullable = false)
	private boolean deleted;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PollComment() {
	}

	private PollComment(Long pollId, Long userId, String content) {
		this.pollId = pollId;
		this.userId = userId;
		this.content = content;
		this.deleted = false;
	}

	public static PollComment create(Long pollId, Long userId, String content) {
		return new PollComment(pollId, userId, content);
	}

	public void update(String content) {
		this.content = content;
	}

	public void delete() {
		this.deleted = true;
		this.deletedAt = Instant.now();
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

	public Long pollId() {
		return pollId;
	}

	public Long userId() {
		return userId;
	}

	public String content() {
		return deleted ? DELETED_CONTENT : content;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}
}
