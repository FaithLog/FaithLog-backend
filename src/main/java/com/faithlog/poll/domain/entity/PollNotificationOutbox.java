package com.faithlog.poll.domain.entity;

import com.faithlog.poll.domain.type.PollType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
	name = "poll_notification_outbox",
	uniqueConstraints = @UniqueConstraint(name = "uk_poll_notification_outbox_poll", columnNames = "poll_id")
)
public class PollNotificationOutbox {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "poll_id", nullable = false)
	private Long pollId;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "creator_id")
	private Long creatorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "poll_type", nullable = false, length = 40)
	private PollType pollType;

	@Column(name = "poll_title", nullable = false, length = 200)
	private String pollTitle;

	@Column(name = "opened_at", nullable = false)
	private Instant openedAt;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PollNotificationOutbox() {
	}

	private PollNotificationOutbox(
		Long pollId,
		Long campusId,
		Long creatorId,
		PollType pollType,
		String pollTitle,
		Instant openedAt
	) {
		this.pollId = pollId;
		this.campusId = campusId;
		this.creatorId = creatorId;
		this.pollType = pollType;
		this.pollTitle = pollTitle;
		this.openedAt = openedAt;
	}

	public static PollNotificationOutbox create(
		Long pollId,
		Long campusId,
		Long creatorId,
		PollType pollType,
		String pollTitle,
		Instant openedAt
	) {
		return new PollNotificationOutbox(pollId, campusId, creatorId, pollType, pollTitle, openedAt);
	}

	@PrePersist
	void prePersist() {
		createdAt = Instant.now();
	}

	public void markProcessed(Instant processedAt) {
		if (this.processedAt == null) {
			this.processedAt = processedAt;
		}
	}

	public boolean isProcessed() { return processedAt != null; }
	public Long id() { return id; }
	public Long pollId() { return pollId; }
	public Long campusId() { return campusId; }
	public Long creatorId() { return creatorId; }
	public PollType pollType() { return pollType; }
	public String pollTitle() { return pollTitle; }
	public Instant openedAt() { return openedAt; }
	public Instant processedAt() { return processedAt; }
}
