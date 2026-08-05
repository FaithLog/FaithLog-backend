package com.faithlog.poll.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "poll_documents")
public class PollDocument {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
	@Column(name = "campus_id", nullable = false) private Long campusId;
	@Column(name = "poll_id", nullable = false) private Long pollId;
	@Column(name = "media_asset_id", nullable = false) private Long mediaAssetId;
	@Column(name = "display_order", nullable = false) private int displayOrder;
	@Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

	protected PollDocument() {}

	private PollDocument(Long campusId, Long pollId, Long mediaAssetId, int displayOrder) {
		this.campusId = campusId;
		this.pollId = pollId;
		this.mediaAssetId = mediaAssetId;
		this.displayOrder = displayOrder;
	}

	public static PollDocument create(Long campusId, Long pollId, Long mediaAssetId, int displayOrder) {
		return new PollDocument(campusId, pollId, mediaAssetId, displayOrder);
	}

	@PrePersist void prePersist() { createdAt = Instant.now(); }
	public Long campusId() { return campusId; }
	public Long pollId() { return pollId; }
	public Long mediaAssetId() { return mediaAssetId; }
	public int displayOrder() { return displayOrder; }
}
