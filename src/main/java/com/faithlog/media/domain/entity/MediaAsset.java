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
import java.time.Duration;
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

	@Column(name = "cleanup_attempt_count", nullable = false)
	private int cleanupAttemptCount;

	@Column(name = "cleanup_next_attempt_at")
	private Instant cleanupNextAttemptAt;

	@Column(name = "cleanup_last_failed_at")
	private Instant cleanupLastFailedAt;

	@Column(name = "cleanup_failure_code", length = 40)
	private String cleanupFailureCode;

	@Column(name = "cleanup_lease_token", length = 64)
	private String cleanupLeaseToken;

	@Column(name = "cleanup_lease_expires_at")
	private Instant cleanupLeaseExpiresAt;

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
		this.cleanupAttemptCount = 0;
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

	public void recordProcessingObjectKeys(String thumbnailObjectKey, String detailObjectKey) {
		if (status != MediaAssetStatus.PROCESSING) {
			throw new IllegalStateException("only processing media can record variant object keys");
		}
		if (thumbnailObjectKey == null || thumbnailObjectKey.isBlank()
			|| detailObjectKey == null || detailObjectKey.isBlank()) {
			throw new IllegalArgumentException("variant object keys are required");
		}
		if ((this.thumbnailObjectKey != null && !this.thumbnailObjectKey.equals(thumbnailObjectKey))
			|| (this.detailObjectKey != null && !this.detailObjectKey.equals(detailObjectKey))) {
			throw new IllegalStateException("variant object keys cannot change during processing");
		}
		this.thumbnailObjectKey = thumbnailObjectKey;
		this.detailObjectKey = detailObjectKey;
		this.updatedAt = Instant.now();
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
		recordProcessingObjectKeys(thumbnailObjectKey, detailObjectKey);
		this.width = width;
		this.height = height;
		this.outputByteSize = outputByteSize;
		this.outputSha256 = outputSha256;
		this.status = MediaAssetStatus.READY;
		this.failureReason = null;
		this.updatedAt = Instant.now();
	}

	public void clearTemporaryObjectKey() {
		if (status != MediaAssetStatus.READY) {
			throw new IllegalStateException("only ready media can clear its temporary object");
		}
		this.temporaryObjectKey = null;
		clearCleanupState();
		this.updatedAt = Instant.now();
	}

	public boolean claimCleanup(String leaseToken, Instant now, Duration leaseDuration) {
		if (leaseToken == null || leaseToken.isBlank()) {
			throw new IllegalArgumentException("cleanup lease token is required");
		}
		if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
			throw new IllegalArgumentException("cleanup lease duration must be positive");
		}
		if (cleanupNextAttemptAt != null && cleanupNextAttemptAt.isAfter(now)) {
			return false;
		}
		if (cleanupLeaseExpiresAt != null && cleanupLeaseExpiresAt.isAfter(now)) {
			return false;
		}
		cleanupLeaseToken = leaseToken;
		cleanupLeaseExpiresAt = now.plus(leaseDuration);
		updatedAt = now;
		return true;
	}

	public void recordCleanupFailure(
		String leaseToken,
		Instant failedAt,
		Instant nextAttemptAt,
		String failureCode
	) {
		requireCleanupLease(leaseToken);
		if (nextAttemptAt == null || !nextAttemptAt.isAfter(failedAt)) {
			throw new IllegalArgumentException("cleanup next attempt must follow failure");
		}
		if (failureCode == null || failureCode.isBlank() || failureCode.length() > 40) {
			throw new IllegalArgumentException("cleanup failure code is invalid");
		}
		cleanupAttemptCount = Math.addExact(cleanupAttemptCount, 1);
		cleanupLastFailedAt = failedAt;
		cleanupNextAttemptAt = nextAttemptAt;
		cleanupFailureCode = failureCode;
		cleanupLeaseToken = null;
		cleanupLeaseExpiresAt = null;
		updatedAt = failedAt;
	}

	public boolean ownsCleanupLease(String leaseToken) {
		return cleanupLeaseToken != null && cleanupLeaseToken.equals(leaseToken);
	}

	public void releaseCleanupLease(String leaseToken, Instant now) {
		requireCleanupLease(leaseToken);
		cleanupLeaseToken = null;
		cleanupLeaseExpiresAt = null;
		updatedAt = now;
	}

	private void clearCleanupState() {
		cleanupAttemptCount = 0;
		cleanupNextAttemptAt = null;
		cleanupLastFailedAt = null;
		cleanupFailureCode = null;
		cleanupLeaseToken = null;
		cleanupLeaseExpiresAt = null;
	}

	private void requireCleanupLease(String leaseToken) {
		if (!ownsCleanupLease(leaseToken)) {
			throw new IllegalStateException("cleanup lease does not match");
		}
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
		markOrphaned(Instant.now());
	}

	public void markOrphaned(Instant orphanedAt) {
		if (status != MediaAssetStatus.READY) {
			throw new IllegalStateException("only ready media can become orphaned");
		}
		status = MediaAssetStatus.ORPHANED;
		this.orphanedAt = java.util.Objects.requireNonNull(orphanedAt);
		updatedAt = this.orphanedAt;
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
	public int cleanupAttemptCount() { return cleanupAttemptCount; }
	public Instant cleanupNextAttemptAt() { return cleanupNextAttemptAt; }
	public Instant cleanupLastFailedAt() { return cleanupLastFailedAt; }
	public String cleanupFailureCode() { return cleanupFailureCode; }
	public String cleanupLeaseToken() { return cleanupLeaseToken; }
	public Instant cleanupLeaseExpiresAt() { return cleanupLeaseExpiresAt; }
}
