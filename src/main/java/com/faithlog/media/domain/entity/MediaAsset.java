package com.faithlog.media.domain.entity;

import com.faithlog.media.domain.type.MediaAssetStatus;
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
import java.util.Set;
import java.util.regex.Pattern;

@Entity
@Table(name = "media_assets")
public class MediaAsset {

	public static final long MAX_INPUT_BYTES = 5L * 1024 * 1024;
	private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
	private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "campus_id", nullable = false)
	private Long campusId;

	@Column(name = "owner_user_id", nullable = false)
	private Long ownerUserId;

	@Column(name = "input_content_type", nullable = false, length = 20)
	private String inputContentType;

	@Column(name = "input_byte_size", nullable = false)
	private long inputByteSize;

	@Column(name = "expected_sha256", nullable = false, length = 64)
	private String expectedSha256;

	@Column(name = "temporary_object_key", unique = true, length = 200)
	private String temporaryObjectKey;

	@Column(name = "thumbnail_object_key", unique = true, length = 200)
	private String thumbnailObjectKey;

	@Column(name = "detail_object_key", unique = true, length = 200)
	private String detailObjectKey;

	@Column(name = "output_sha256", length = 64)
	private String outputSha256;

	@Column private Integer width;
	@Column private Integer height;
	@Column(name = "output_byte_size") private Long outputByteSize;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MediaAssetStatus status;

	@Column(name = "failure_reason", length = 100)
	private String failureReason;

	@Column(name = "orphaned_at")
	private Instant orphanedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MediaAsset() {
	}

	private MediaAsset(
		Long campusId,
		Long ownerUserId,
		String inputContentType,
		long inputByteSize,
		String expectedSha256,
		String temporaryObjectKey,
		Instant expiresAt
	) {
		this.campusId = requirePositive(campusId, "campusId");
		this.ownerUserId = requirePositive(ownerUserId, "ownerUserId");
		if (!SUPPORTED_CONTENT_TYPES.contains(inputContentType)) {
			throw new IllegalArgumentException("unsupported image content type");
		}
		if (inputByteSize < 1 || inputByteSize > MAX_INPUT_BYTES) {
			throw new IllegalArgumentException("image byte size is invalid");
		}
		if (expectedSha256 == null || !SHA_256.matcher(expectedSha256).matches()) {
			throw new IllegalArgumentException("expectedSha256 is invalid");
		}
		if (temporaryObjectKey == null || temporaryObjectKey.isBlank()) {
			throw new IllegalArgumentException("temporaryObjectKey is required");
		}
		this.inputContentType = inputContentType;
		this.inputByteSize = inputByteSize;
		this.expectedSha256 = expectedSha256;
		this.temporaryObjectKey = temporaryObjectKey;
		this.expiresAt = java.util.Objects.requireNonNull(expiresAt);
		this.status = MediaAssetStatus.PENDING;
	}

	public static MediaAsset reserve(
		Long campusId,
		Long ownerUserId,
		String inputContentType,
		long inputByteSize,
		String expectedSha256,
		String temporaryObjectKey,
		Instant expiresAt
	) {
		return new MediaAsset(
			campusId, ownerUserId, inputContentType, inputByteSize, expectedSha256, temporaryObjectKey, expiresAt);
	}

	public void startProcessing() {
		if (status != MediaAssetStatus.PENDING) {
			throw new IllegalStateException("only pending media can start processing");
		}
		status = MediaAssetStatus.PROCESSING;
		updatedAt = Instant.now();
	}

	public void complete(
		String thumbnailObjectKey,
		String detailObjectKey,
		int width,
		int height,
		long outputByteSize,
		String outputSha256
	) {
		if (status != MediaAssetStatus.PROCESSING) {
			throw new IllegalStateException("only processing media can complete");
		}
		if (thumbnailObjectKey == null || thumbnailObjectKey.isBlank()
			|| detailObjectKey == null || detailObjectKey.isBlank()) {
			throw new IllegalArgumentException("variant object keys are required");
		}
		if (width < 1 || width > 4096 || height < 1 || height > 4096 || outputByteSize < 1) {
			throw new IllegalArgumentException("output metadata is invalid");
		}
		if (outputSha256 == null || !SHA_256.matcher(outputSha256).matches()) {
			throw new IllegalArgumentException("outputSha256 is invalid");
		}
		this.thumbnailObjectKey = thumbnailObjectKey;
		this.detailObjectKey = detailObjectKey;
		this.width = width;
		this.height = height;
		this.outputByteSize = outputByteSize;
		this.outputSha256 = outputSha256;
		this.temporaryObjectKey = null;
		this.status = MediaAssetStatus.READY;
		this.failureReason = null;
		this.updatedAt = Instant.now();
	}

	public void markFailed(String reason) {
		if (status != MediaAssetStatus.PROCESSING) {
			throw new IllegalStateException("only processing media can fail");
		}
		status = MediaAssetStatus.FAILED;
		failureReason = reason;
		updatedAt = Instant.now();
	}

	public void markOrphaned() {
		if (status != MediaAssetStatus.READY) {
			throw new IllegalStateException("only ready media can become orphaned");
		}
		status = MediaAssetStatus.ORPHANED;
		orphanedAt = Instant.now();
		updatedAt = orphanedAt;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	private static Long requirePositive(Long value, String field) {
		if (value == null || value <= 0) {
			throw new IllegalArgumentException(field + " must be positive");
		}
		return value;
	}

	public Long id() { return id; }
	public Long campusId() { return campusId; }
	public Long ownerUserId() { return ownerUserId; }
	public String inputContentType() { return inputContentType; }
	public long inputByteSize() { return inputByteSize; }
	public String expectedSha256() { return expectedSha256; }
	public String temporaryObjectKey() { return temporaryObjectKey; }
	public String thumbnailObjectKey() { return thumbnailObjectKey; }
	public String detailObjectKey() { return detailObjectKey; }
	public MediaAssetStatus status() { return status; }
	public Instant expiresAt() { return expiresAt; }
	public Instant orphanedAt() { return orphanedAt; }
	public Integer width() { return width; }
	public Integer height() { return height; }
	public Long outputByteSize() { return outputByteSize; }
	public String outputSha256() { return outputSha256; }
}
