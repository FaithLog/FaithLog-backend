package com.faithlog.announcement.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "announcement_images")
public class AnnouncementImage {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@Column(name = "campus_id", nullable = false) private Long campusId;
	@Column(name = "announcement_id", nullable = false) private Long announcementId;
	@Column(name = "media_asset_id", nullable = false) private Long mediaAssetId;
	@Column(name = "display_order", nullable = false) private int displayOrder;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
	protected AnnouncementImage() {}
	private AnnouncementImage(Long campusId, Long announcementId, Long mediaAssetId, int displayOrder) {
		this.campusId = campusId;
		this.announcementId = announcementId; this.mediaAssetId = mediaAssetId; this.displayOrder = displayOrder;
	}
	public static AnnouncementImage create(Long campusId, Long announcementId, Long mediaAssetId, int displayOrder) {
		return new AnnouncementImage(campusId, announcementId, mediaAssetId, displayOrder);
	}
	@PrePersist void prePersist() { createdAt = Instant.now(); }
	public Long campusId() { return campusId; }
	public Long announcementId() { return announcementId; }
	public Long mediaAssetId() { return mediaAssetId; }
	public int displayOrder() { return displayOrder; }
}
