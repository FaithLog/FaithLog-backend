package com.faithlog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.ImageVariantProcessorPort;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import com.faithlog.media.service.port.MediaUploadRateLimitPort;
import com.faithlog.media.service.port.PdfDocumentValidatorPort;
import com.faithlog.media.service.policy.MediaAssetAccessPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class MediaAssetCommandServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
	private static final byte[] SOURCE = "source-image".getBytes(StandardCharsets.UTF_8);
	@Mock private MediaAssetRepositoryPort repository;
	@Mock private MediaObjectStoragePort storage;
	@Mock private ImageVariantProcessorPort imageProcessor;
	@Mock private PdfDocumentValidatorPort pdfValidator;
	@Mock private MediaUploadRateLimitPort rateLimit;
	@Mock private MediaAssetAccessPolicy accessPolicy;
	@Mock private PlatformTransactionManager transactionManager;
	@Mock private TransactionStatus transactionStatus;
	private MediaAssetCommandService service;
	private MediaAsset asset;

	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
		service = new MediaAssetCommandService(repository, storage, imageProcessor, pdfValidator, rateLimit, accessPolicy,
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
		asset = MediaAsset.reserve(7L, 11L, "image/jpeg", SOURCE.length, sha256(SOURCE),
			"temporary/asset/original", NOW.plusSeconds(3600));
		ReflectionTestUtils.setField(asset, "id", 31L);
		when(repository.findByCampusIdAndIdForUpdate(7L, 31L)).thenReturn(Optional.of(asset));
		lenient().when(storage.getObject("temporary/asset/original", MediaAsset.MAX_INPUT_BYTES))
			.thenReturn(new MediaObjectStoragePort.StoredObject("image/jpeg", SOURCE));
		lenient().when(imageProcessor.process(SOURCE, "image/jpeg")).thenReturn(new ImageVariantProcessorPort.ProcessedVariants(
			new byte[]{1, 2}, new byte[]{3, 4}, 100, 80, 100, 80, 100, 80, "image/jpeg"));
	}

	@Test
	void partial_variant_upload_is_compensated_and_asset_becomes_failed() {
		doThrow(new IllegalStateException("provider unavailable"))
			.when(storage).putObject(contains("/detail.jpg"), anyString(), any(byte[].class));

		assertThatThrownBy(() -> service.complete(7L, 31L, 11L))
			.isInstanceOf(BusinessException.class);

		ArgumentCaptor<String> thumbnailKey = ArgumentCaptor.forClass(String.class);
		verify(storage).putObject(thumbnailKey.capture(), org.mockito.ArgumentMatchers.eq("image/jpeg"),
			any(byte[].class));
		verify(storage).deleteObject(thumbnailKey.getValue());
		assertThat(asset.status()).isEqualTo(MediaAssetStatus.FAILED);
	}

	@Test
	void failed_compensation_keeps_variant_keys_for_durable_cleanup() {
		doThrow(new IllegalStateException("provider unavailable"))
			.when(storage).putObject(contains("/detail.jpg"), anyString(), any(byte[].class));
		doThrow(new IllegalStateException("delete unavailable"))
			.when(storage).deleteObject(anyString());

		assertThatThrownBy(() -> service.complete(7L, 31L, 11L))
			.isInstanceOf(BusinessException.class);

		assertThat(asset.status()).isEqualTo(MediaAssetStatus.FAILED);
		assertThat(asset.thumbnailObjectKey()).startsWith("media/").endsWith("/thumbnail.jpg");
		assertThat(asset.detailObjectKey()).startsWith("media/").endsWith("/detail.jpg");
	}

	@Test
	void ready_state_is_committed_before_temporary_original_is_deleted() {
		doAnswer(invocation -> {
			assertThat(asset.status()).isEqualTo(MediaAssetStatus.READY);
			return null;
		}).when(storage).deleteObject("temporary/asset/original");

		var result = service.complete(7L, 31L, 11L);

		assertThat(result.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(asset.temporaryObjectKey()).isNull();
	}

	@Test
	void ready_result_is_preserved_when_temporary_original_deletion_fails() {
		doThrow(new IllegalStateException("delete unavailable"))
			.when(storage).deleteObject("temporary/asset/original");

		var result = service.complete(7L, 31L, 11L);

		assertThat(result.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(asset.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(asset.temporaryObjectKey()).isEqualTo("temporary/asset/original");
	}

	@Test
	void already_ready_asset_remains_idempotent_when_temporary_original_deletion_fails() {
		asset.startProcessing();
		asset.recordProcessingObjectKeys("media/asset/thumbnail.jpg", "media/asset/detail.jpg");
		asset.complete("media/asset/thumbnail.jpg", "media/asset/detail.jpg", 100, 80, 6, "b".repeat(64));
		doThrow(new IllegalStateException("delete unavailable"))
			.when(storage).deleteObject("temporary/asset/original");

		var result = service.complete(7L, 31L, 11L);

		assertThat(result.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(asset.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(asset.temporaryObjectKey()).isEqualTo("temporary/asset/original");
	}

	@Test
	void expired_pending_reservation_is_rejected_before_storage_or_processing() {
		asset = MediaAsset.reserve(7L, 11L, "image/jpeg", SOURCE.length, sha256(SOURCE),
			"temporary/expired/original", NOW);
		ReflectionTestUtils.setField(asset, "id", 31L);
		when(repository.findByCampusIdAndIdForUpdate(7L, 31L)).thenReturn(Optional.of(asset));

		assertThatThrownBy(() -> service.complete(7L, 31L, 11L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(com.faithlog.global.exception.ErrorCode.MEDIA_ASSET_STATE_CONFLICT));

		assertThat(asset.status()).isEqualTo(MediaAssetStatus.PENDING);
		verifyNoInteractions(storage, imageProcessor);
	}

	@Test
	void pdf_is_validated_and_stored_as_a_single_private_document_without_image_processing() {
		byte[] pdf = "%PDF-1.7\nplain".getBytes(StandardCharsets.US_ASCII);
		asset = MediaAsset.reserve(7L, 11L, "application/pdf", pdf.length, sha256(pdf),
			"temporary/pdf/original", NOW.plusSeconds(3600), "안내문.pdf");
		ReflectionTestUtils.setField(asset, "id", 31L);
		when(repository.findByCampusIdAndIdForUpdate(7L, 31L)).thenReturn(Optional.of(asset));
		when(storage.getObject("temporary/pdf/original", MediaAsset.MAX_PDF_INPUT_BYTES))
			.thenReturn(new MediaObjectStoragePort.StoredObject("application/pdf", pdf));
		when(pdfValidator.validate(pdf, "application/pdf"))
			.thenReturn(new PdfDocumentValidatorPort.ValidatedPdf(1));

		var result = service.complete(7L, 31L, 11L);

		assertThat(result.assetKind()).isEqualTo(com.faithlog.media.domain.type.MediaAssetKind.PDF);
		assertThat(result.fileName()).isEqualTo("안내문.pdf");
		assertThat(asset.documentObjectKey()).startsWith("media/").endsWith("/document.pdf");
		verify(storage).putObject(org.mockito.ArgumentMatchers.eq(asset.documentObjectKey()),
			org.mockito.ArgumentMatchers.eq("application/pdf"), org.mockito.ArgumentMatchers.eq(pdf));
		verifyNoInteractions(imageProcessor);
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}
}
