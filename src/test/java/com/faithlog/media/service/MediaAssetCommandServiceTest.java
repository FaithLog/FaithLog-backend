package com.faithlog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.service.port.ImageVariantProcessorPort;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import com.faithlog.media.service.port.MediaUploadRateLimitPort;
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
	@Mock private MediaUploadRateLimitPort rateLimit;
	@Mock private AnnouncementAccessPolicy accessPolicy;
	@Mock private PlatformTransactionManager transactionManager;
	@Mock private TransactionStatus transactionStatus;
	private MediaAssetCommandService service;
	private MediaAsset asset;

	@BeforeEach
	void setUp() {
		when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
		service = new MediaAssetCommandService(repository, storage, imageProcessor, rateLimit, accessPolicy,
			transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
		asset = MediaAsset.reserve(7L, 11L, "image/jpeg", SOURCE.length, sha256(SOURCE),
			"temporary/asset/original", NOW.plusSeconds(3600));
		ReflectionTestUtils.setField(asset, "id", 31L);
		when(repository.findByCampusIdAndIdForUpdate(7L, 31L)).thenReturn(Optional.of(asset));
		when(storage.getObject("temporary/asset/original", MediaAsset.MAX_INPUT_BYTES))
			.thenReturn(new MediaObjectStoragePort.StoredObject("image/jpeg", SOURCE));
		when(imageProcessor.process(SOURCE, "image/jpeg")).thenReturn(new ImageVariantProcessorPort.ProcessedVariants(
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
	void ready_state_is_committed_before_temporary_original_is_deleted() {
		doAnswer(invocation -> {
			assertThat(asset.status()).isEqualTo(MediaAssetStatus.READY);
			return null;
		}).when(storage).deleteObject("temporary/asset/original");

		var result = service.complete(7L, 31L, 11L);

		assertThat(result.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(asset.temporaryObjectKey()).isNull();
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception exception) {
			throw new AssertionError(exception);
		}
	}
}
