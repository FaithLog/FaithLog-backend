package com.faithlog.weeklymaterial.domain.entity;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "weekly_material_notification_outbox")
public class WeeklyMaterialNotificationOutbox {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "campus_id", nullable = false) private Long publisherCampusId;
	@Column(name = "weekly_material_id", nullable = false) private Long weeklyMaterialId;
	@Column(name = "week_start_date", nullable = false) private LocalDate weekStartDate;
	@Enumerated(EnumType.STRING)
	@Column(name = "material_type", nullable = false, length = 30)
	private WeeklyMaterialType materialType;
	@Column(name = "uploader_id", nullable = false) private Long uploaderId;
	@Column(name = "processed_at") private Instant processedAt;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

	protected WeeklyMaterialNotificationOutbox() {}

	private WeeklyMaterialNotificationOutbox(Long publisherCampusId, Long weeklyMaterialId, LocalDate weekStartDate,
		Long uploaderId) {
		this.publisherCampusId = publisherCampusId;
		this.weeklyMaterialId = weeklyMaterialId;
		this.weekStartDate = weekStartDate;
		this.materialType = WeeklyMaterialType.SUNDAY_SHARING_SHEET;
		this.uploaderId = uploaderId;
	}

	public static WeeklyMaterialNotificationOutbox create(Long publisherCampusId, Long weeklyMaterialId,
		LocalDate weekStartDate, Long uploaderId) {
		return new WeeklyMaterialNotificationOutbox(publisherCampusId, weeklyMaterialId, weekStartDate, uploaderId);
	}

	@PrePersist void prePersist() { createdAt = Instant.now(); }
	public void markProcessed(Instant now) { if (processedAt == null) processedAt = now; }
	public boolean isProcessed() { return processedAt != null; }
	public Long id() { return id; }
	public Long publisherCampusId() { return publisherCampusId; }
	public Long weeklyMaterialId() { return weeklyMaterialId; }
	public LocalDate weekStartDate() { return weekStartDate; }
	public WeeklyMaterialType materialType() { return materialType; }
	public Long uploaderId() { return uploaderId; }
	public Instant processedAt() { return processedAt; }
}
