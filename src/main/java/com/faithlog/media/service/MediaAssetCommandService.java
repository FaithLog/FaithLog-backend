package com.faithlog.media.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetKind;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.ImageVariantProcessorPort;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import com.faithlog.media.service.port.MediaUploadRateLimitPort;
import com.faithlog.media.service.port.PdfDocumentValidatorPort;
import com.faithlog.media.service.policy.MediaAssetAccessPolicy;
import com.faithlog.media.service.result.MediaAssetResult;
import com.faithlog.media.service.result.MediaUploadReservationResult;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MediaAssetCommandService {

	private static final Duration TEMPORARY_TTL = Duration.ofHours(24);

	private final MediaAssetRepositoryPort repository;
	private final MediaObjectStoragePort storage;
	private final ImageVariantProcessorPort imageProcessor;
	private final PdfDocumentValidatorPort pdfValidator;
	private final MediaUploadRateLimitPort rateLimit;
	private final MediaAssetAccessPolicy accessPolicy;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	@Autowired
	public MediaAssetCommandService(
		MediaAssetRepositoryPort repository,
		MediaObjectStoragePort storage,
		ImageVariantProcessorPort imageProcessor,
		PdfDocumentValidatorPort pdfValidator,
		MediaUploadRateLimitPort rateLimit,
		MediaAssetAccessPolicy accessPolicy,
		PlatformTransactionManager transactionManager,
		Clock clock
	) {
		this.repository = repository;
		this.storage = storage;
		this.imageProcessor = imageProcessor;
		this.pdfValidator = pdfValidator;
		this.rateLimit = rateLimit;
		this.accessPolicy = accessPolicy;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	public MediaAssetCommandService(
		MediaAssetRepositoryPort repository,
		MediaObjectStoragePort storage,
		ImageVariantProcessorPort imageProcessor,
		MediaUploadRateLimitPort rateLimit,
		MediaAssetAccessPolicy accessPolicy,
		PlatformTransactionManager transactionManager,
		Clock clock
	) {
		this(repository, storage, imageProcessor,
			(source, contentType) -> { throw new IllegalArgumentException("PDF validation is unavailable"); },
			rateLimit, accessPolicy, transactionManager, clock);
	}

	public MediaUploadReservationResult reserve(
		Long campusId, Long requesterId, String contentType, long byteSize, String sha256
	) {
		return reserve(campusId, requesterId, contentType, byteSize, sha256, null);
	}

	public MediaUploadReservationResult reserve(
		Long campusId, Long requesterId, String contentType, long byteSize, String sha256, String fileName
	) {
		accessPolicy.requireUploadPermission(campusId, requesterId);
		if (!rateLimit.acquire(campusId, requesterId)) {
			throw new BusinessException(ErrorCode.MEDIA_UPLOAD_RATE_LIMITED);
		}
		String key = "temporary/" + UUID.randomUUID() + "/original";
		Instant expiresAt = clock.instant().plus(TEMPORARY_TTL);
		MediaAsset asset;
		try {
			asset = transactionTemplate.execute(status -> repository.save(
				MediaAsset.reserve(campusId, requesterId, contentType, byteSize, sha256, key, expiresAt, fileName)));
		} catch (IllegalArgumentException exception) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_INVALID);
		}
		try {
			var upload = storage.presignUpload(key, contentType, byteSize);
			return new MediaUploadReservationResult(asset.id(), upload.url(), upload.requiredHeaders(), upload.expiresAt());
		} catch (RuntimeException exception) {
			throw new BusinessException(ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
		}
	}

	public MediaAssetResult complete(Long campusId, Long assetId, Long requesterId) {
		accessPolicy.requireUploadPermission(campusId, requesterId);
		FinalizeSnapshot snapshot = transactionTemplate.execute(status -> claim(campusId, assetId, requesterId));
		if (snapshot.readyResult() != null) {
			deleteTemporaryOriginal(campusId, assetId, snapshot.temporaryKey());
			return snapshot.readyResult();
		}
		String variantRoot = "media/" + UUID.randomUUID();
		if (snapshot.kind() == MediaAssetKind.PDF) {
			return completePdf(campusId, assetId, snapshot, variantRoot + "/document.pdf");
		}
		String thumbnailKey = variantRoot + "/thumbnail.jpg";
		String detailKey = variantRoot + "/detail.jpg";
		transactionTemplate.executeWithoutResult(status -> {
			MediaAsset asset = requireForUpdate(campusId, assetId);
			asset.recordProcessingObjectKeys(thumbnailKey, detailKey);
		});
		boolean readyCommitted = false;
		try {
			var stored = storage.getObject(snapshot.temporaryKey(), MediaAsset.MAX_INPUT_BYTES);
			byte[] content = stored.content();
			validateUploadedObject(snapshot, stored.contentType(), content);
			var variants = imageProcessor.process(content, snapshot.contentType());
			storage.putObject(thumbnailKey, variants.outputContentType(), variants.thumbnailBytes());
			storage.putObject(detailKey, variants.outputContentType(), variants.detailBytes());
			MediaAssetResult result = transactionTemplate.execute(status -> {
				MediaAsset asset = requireForUpdate(campusId, assetId);
				asset.complete(thumbnailKey, detailKey, variants.sourceWidth(), variants.sourceHeight(),
					(long) variants.thumbnailBytes().length + variants.detailBytes().length,
					sha256(variants.detailBytes()));
				return MediaAssetResult.from(asset);
			});
			readyCommitted = true;
			deleteTemporaryOriginal(campusId, assetId, snapshot.temporaryKey());
			return result;
		} catch (RuntimeException exception) {
			if (!readyCommitted) {
				compensateVariant(thumbnailKey);
				compensateVariant(detailKey);
				transactionTemplate.executeWithoutResult(status -> {
					MediaAsset asset = requireForUpdate(campusId, assetId);
					if (asset.status() == MediaAssetStatus.PROCESSING) {
						asset.markFailed("PROCESSING_FAILED");
					}
				});
			}
			if (exception instanceof BusinessException businessException) {
				throw businessException;
			}
			throw new BusinessException(exception instanceof IllegalArgumentException
				? ErrorCode.MEDIA_ASSET_INVALID : ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
		}
	}

	private MediaAssetResult completePdf(
		Long campusId,
		Long assetId,
		FinalizeSnapshot snapshot,
		String documentKey
	) {
		transactionTemplate.executeWithoutResult(status -> {
			MediaAsset asset = requireForUpdate(campusId, assetId);
			asset.recordProcessingDocumentObjectKey(documentKey);
		});
		boolean readyCommitted = false;
		try {
			var stored = storage.getObject(snapshot.temporaryKey(), MediaAsset.MAX_PDF_INPUT_BYTES);
			byte[] content = stored.content();
			validateUploadedObject(snapshot, stored.contentType(), content);
			pdfValidator.validate(content, snapshot.contentType());
			storage.putObject(documentKey, snapshot.contentType(), content);
			MediaAssetResult result = transactionTemplate.execute(status -> {
				MediaAsset asset = requireForUpdate(campusId, assetId);
				asset.completePdf(documentKey, content.length, sha256(content));
				return MediaAssetResult.from(asset);
			});
			readyCommitted = true;
			deleteTemporaryOriginal(campusId, assetId, snapshot.temporaryKey());
			return result;
		} catch (RuntimeException exception) {
			if (!readyCommitted) {
				compensateVariant(documentKey);
				markProcessingFailed(campusId, assetId);
			}
			if (exception instanceof BusinessException businessException) {
				throw businessException;
			}
			throw new BusinessException(exception instanceof IllegalArgumentException
				? ErrorCode.MEDIA_ASSET_INVALID : ErrorCode.MEDIA_STORAGE_UNAVAILABLE);
		}
	}

	private void validateUploadedObject(FinalizeSnapshot snapshot, String storedContentType, byte[] content) {
		if (!snapshot.contentType().equals(storedContentType) || content.length != snapshot.byteSize()
			|| !snapshot.sha256().equals(sha256(content))) {
			throw new IllegalArgumentException("uploaded object metadata does not match reservation");
		}
	}

	private void markProcessingFailed(Long campusId, Long assetId) {
		transactionTemplate.executeWithoutResult(status -> {
			MediaAsset asset = requireForUpdate(campusId, assetId);
			if (asset.status() == MediaAssetStatus.PROCESSING) {
				asset.markFailed("PROCESSING_FAILED");
			}
		});
	}

	private void deleteTemporaryOriginal(Long campusId, Long assetId, String temporaryKey) {
		if (temporaryKey == null) {
			return;
		}
		try {
			storage.deleteObject(temporaryKey);
			transactionTemplate.executeWithoutResult(status -> {
				MediaAsset asset = requireForUpdate(campusId, assetId);
				if (asset.status() != MediaAssetStatus.READY) {
					throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
				}
				if (temporaryKey.equals(asset.temporaryObjectKey())) {
					asset.clearTemporaryObjectKey();
				}
			});
		} catch (RuntimeException ignored) {
			// READY is already durable; the tracked temporary key is retried by scheduled cleanup.
		}
	}

	private void compensateVariant(String objectKey) {
		try {
			storage.deleteObject(objectKey);
		} catch (RuntimeException ignored) {
			// The FAILED row remains as durable evidence; cleanup retries tracked objects.
		}
	}

	private FinalizeSnapshot claim(Long campusId, Long assetId, Long requesterId) {
		MediaAsset asset = requireForUpdate(campusId, assetId);
		if (!asset.ownerUserId().equals(requesterId)) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN);
		}
		if (asset.status() == MediaAssetStatus.READY) {
			return new FinalizeSnapshot(
				asset.temporaryObjectKey(), asset.kind(), null, 0, null, MediaAssetResult.from(asset));
		}
		if (asset.status() != MediaAssetStatus.PENDING) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
		}
		if (!asset.expiresAt().isAfter(clock.instant())) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT);
		}
		asset.startProcessing();
		return new FinalizeSnapshot(asset.temporaryObjectKey(), asset.kind(), asset.inputContentType(), asset.inputByteSize(),
			asset.expectedSha256(), null);
	}

	private MediaAsset requireForUpdate(Long campusId, Long assetId) {
		return repository.findByCampusIdAndIdForUpdate(campusId, assetId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEDIA_ASSET_NOT_FOUND));
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private record FinalizeSnapshot(
		String temporaryKey,
		MediaAssetKind kind,
		String contentType,
		long byteSize,
		String sha256,
		MediaAssetResult readyResult
	) {
	}
}
