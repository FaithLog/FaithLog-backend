package com.faithlog.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
	name = "notification_logs",
	indexes = {
		@Index(name = "idx_notification_logs_request_id", columnList = "request_id"),
		@Index(
			name = "idx_notification_logs_campus_type_status_created",
			columnList = "campus_id, notification_type, send_status, created_at"
		)
	}
)
public class NotificationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "request_id", nullable = false)
	private UUID requestId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_type", nullable = false, length = 50)
	private NotificationType notificationType;

	@Column(name = "target_week_start_date")
	private LocalDate targetWeekStartDate;

	@Column(name = "target_id")
	private Long targetId;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, columnDefinition = "text")
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(name = "send_status", nullable = false, length = 30)
	private SendStatus sendStatus;

	@Column(name = "failure_reason", columnDefinition = "text")
	private String failureReason;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected NotificationLog() {
	}

	private NotificationLog(
		UUID requestId,
		Long userId,
		Long campusId,
		NotificationType notificationType,
		LocalDate targetWeekStartDate,
		Long targetId,
		String title,
		String body,
		SendStatus sendStatus,
		String failureReason
	) {
		this.requestId = requestId;
		this.userId = userId;
		this.campusId = campusId;
		this.notificationType = notificationType;
		this.targetWeekStartDate = targetWeekStartDate;
		this.targetId = targetId;
		this.title = title;
		this.body = body;
		this.sendStatus = sendStatus;
		this.failureReason = failureReason;
	}

	public static NotificationLog pending(
		UUID requestId,
		Long userId,
		Long campusId,
		NotificationType notificationType,
		LocalDate targetWeekStartDate,
		Long targetId,
		String title,
		String body
	) {
		return new NotificationLog(
			requestId,
			userId,
			campusId,
			notificationType,
			targetWeekStartDate,
			targetId,
			title,
			body,
			SendStatus.PENDING,
			null
		);
	}

	public static NotificationLog skipped(
		UUID requestId,
		Long userId,
		Long campusId,
		NotificationType notificationType,
		LocalDate targetWeekStartDate,
		Long targetId,
		String title,
		String body,
		String reason
	) {
		return new NotificationLog(
			requestId,
			userId,
			campusId,
			notificationType,
			targetWeekStartDate,
			targetId,
			title,
			body,
			SendStatus.SKIPPED,
			reason
		);
	}

	@PrePersist
	void prePersist() {
		this.createdAt = Instant.now();
	}

	public void markSent() {
		this.sendStatus = SendStatus.SENT;
		this.failureReason = null;
		this.sentAt = Instant.now();
	}

	public void markFailed(String failureReason) {
		this.sendStatus = SendStatus.FAILED;
		this.failureReason = failureReason;
	}

	public void markSkipped(String failureReason) {
		this.sendStatus = SendStatus.SKIPPED;
		this.failureReason = failureReason;
	}

	public Long id() {
		return id;
	}

	public UUID requestId() {
		return requestId;
	}

	public Long userId() {
		return userId;
	}

	public Long campusId() {
		return campusId;
	}

	public NotificationType notificationType() {
		return notificationType;
	}

	public LocalDate targetWeekStartDate() {
		return targetWeekStartDate;
	}

	public Long targetId() {
		return targetId;
	}

	public String title() {
		return title;
	}

	public String body() {
		return body;
	}

	public SendStatus sendStatus() {
		return sendStatus;
	}

	public String failureReason() {
		return failureReason;
	}

	public Instant sentAt() {
		return sentAt;
	}

	public Instant createdAt() {
		return createdAt;
	}
}
