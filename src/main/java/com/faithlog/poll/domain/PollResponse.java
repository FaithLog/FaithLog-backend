package com.faithlog.poll.domain;

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
	name = "poll_responses",
	uniqueConstraints = {
		@UniqueConstraint(name = "uk_poll_responses_poll_user", columnNames = {"poll_id", "user_id"})
	}
)
public class PollResponse {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "poll_id", nullable = false)
	private Long pollId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(length = 500)
	private String memo;

	@Column(name = "responded_at", nullable = false)
	private Instant respondedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected PollResponse() {
	}

	private PollResponse(Long pollId, Long userId, String memo) {
		this.pollId = pollId;
		this.userId = userId;
		updateMemo(memo);
	}

	public static PollResponse create(Long pollId, Long userId, String memo) {
		return new PollResponse(pollId, userId, memo);
	}

	public void updateMemo(String memo) {
		this.memo = memo;
		this.respondedAt = Instant.now();
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (respondedAt == null) {
			this.respondedAt = now;
		}
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

	public String memo() {
		return memo;
	}

	public Instant respondedAt() {
		return respondedAt;
	}
}
