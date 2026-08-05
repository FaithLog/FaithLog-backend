package com.faithlog.weeklymaterial.domain.entity;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialStatus;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
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
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "weekly_materials")
public class WeeklyMaterial {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "week_start_date", nullable = false)
	private LocalDate weekStartDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "material_type", nullable = false, length = 30)
	private WeeklyMaterialType materialType;

	@Column(name = "media_asset_id")
	private Long mediaAssetId;

	@Column(name = "uploaded_by", nullable = false)
	private Long uploadedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private WeeklyMaterialStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected WeeklyMaterial() {
	}

	private WeeklyMaterial(Long campusId, LocalDate weekStartDate, WeeklyMaterialType materialType,
		Long mediaAssetId, Long uploadedBy) {
		this.campusId = positive(campusId, "campusId");
		this.weekStartDate = monday(weekStartDate);
		this.materialType = Objects.requireNonNull(materialType);
		this.mediaAssetId = positive(mediaAssetId, "mediaAssetId");
		this.uploadedBy = positive(uploadedBy, "uploadedBy");
		this.status = WeeklyMaterialStatus.ACTIVE;
	}

	public static WeeklyMaterial create(Long campusId, LocalDate weekStartDate, WeeklyMaterialType materialType,
		Long mediaAssetId, Long uploadedBy) {
		return new WeeklyMaterial(campusId, weekStartDate, materialType, mediaAssetId, uploadedBy);
	}

	public Long replaceMedia(Long newMediaAssetId, Long requesterId) {
		if (status != WeeklyMaterialStatus.ACTIVE) throw new IllegalStateException("material is deleted");
		Long old = mediaAssetId;
		mediaAssetId = positive(newMediaAssetId, "mediaAssetId");
		uploadedBy = positive(requesterId, "uploadedBy");
		return old;
	}

	public Long delete() {
		if (status != WeeklyMaterialStatus.ACTIVE) throw new IllegalStateException("material is deleted");
		Long old = mediaAssetId;
		mediaAssetId = null;
		status = WeeklyMaterialStatus.DELETED;
		return old;
	}

	public void reregister(Long newMediaAssetId, Long requesterId) {
		if (status != WeeklyMaterialStatus.DELETED) throw new IllegalStateException("material is active");
		mediaAssetId = positive(newMediaAssetId, "mediaAssetId");
		uploadedBy = positive(requesterId, "uploadedBy");
		status = WeeklyMaterialStatus.ACTIVE;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}

	private static Long positive(Long value, String field) {
		if (value == null || value <= 0) throw new IllegalArgumentException(field + " must be positive");
		return value;
	}

	private static LocalDate monday(LocalDate value) {
		if (value == null || value.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new IllegalArgumentException("weekStartDate must be Monday");
		}
		return value;
	}

	public Long id() { return id; }
	public Long campusId() { return campusId; }
	public LocalDate weekStartDate() { return weekStartDate; }
	public WeeklyMaterialType materialType() { return materialType; }
	public Long mediaAssetId() { return mediaAssetId; }
	public Long uploadedBy() { return uploadedBy; }
	public WeeklyMaterialStatus status() { return status; }
	public Instant createdAt() { return createdAt; }
	public Instant updatedAt() { return updatedAt; }
}
