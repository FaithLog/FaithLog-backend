package com.faithlog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class MediaAssetCleanupServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
	@Mock private MediaAssetRepositoryPort repository;
	@Mock private MediaObjectStoragePort storage;
	@Mock private PlatformTransactionManager transactionManager;
	@Mock private TransactionStatus transactionStatus;

	@Test
	void cleanup_claims_only_expired_temporary_and_24_hour_orphan_candidates() {
		when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
		when(repository.findCleanupCandidateIds(NOW, NOW.minusSeconds(86_400), 100)).thenReturn(List.of(1L, 2L));
		MediaAsset pending = pendingAsset(1L);
		MediaAsset orphaned = orphanedAsset(2L);
		when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));
		when(repository.findByIdForUpdate(2L)).thenReturn(Optional.of(orphaned));

		MediaAssetCleanupService service = new MediaAssetCleanupService(
			repository, storage, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(service.cleanupBatch()).isEqualTo(2);
		verify(storage).deleteObject("temporary/1/original");
		verify(storage).deleteObject("media/2/thumbnail.jpg");
		verify(storage).deleteObject("media/2/detail.jpg");
		verify(repository).delete(pending);
		verify(repository).delete(orphaned);
	}

	@Test
	void cleanup_never_deletes_ready_attached_assets_even_if_repository_returns_a_stale_id() {
		when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
		when(repository.findCleanupCandidateIds(NOW, NOW.minusSeconds(86_400), 100)).thenReturn(List.of(3L));
		MediaAsset ready = readyAsset(3L);
		when(repository.findByIdForUpdate(3L)).thenReturn(Optional.of(ready));

		MediaAssetCleanupService service = new MediaAssetCleanupService(
			repository, storage, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(service.cleanupBatch()).isZero();
		verify(storage, never()).deleteObject(org.mockito.ArgumentMatchers.anyString());
		verify(repository, never()).delete(ready);
	}

	@Test
	void cleanup_removes_only_expired_temporary_original_from_ready_asset() {
		when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
		when(repository.findCleanupCandidateIds(NOW, NOW.minusSeconds(86_400), 100)).thenReturn(List.of(4L));
		MediaAsset ready = readyAsset(4L, NOW.minusSeconds(1));
		when(repository.findByIdForUpdate(4L)).thenReturn(Optional.of(ready));
		MediaAssetCleanupService service = new MediaAssetCleanupService(
			repository, storage, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(service.cleanupBatch()).isEqualTo(1);
		verify(storage).deleteObject("temporary/3/original");
		verify(storage, never()).deleteObject("media/2/thumbnail.jpg");
		verify(storage, never()).deleteObject("media/2/detail.jpg");
		verify(repository, never()).delete(ready);
		assertThat(ready.temporaryObjectKey()).isNull();
		assertThat(ready.thumbnailObjectKey()).isEqualTo("media/2/thumbnail.jpg");
	}

	@Test
	void cleanup_retries_every_tracked_object_for_failed_processing() {
		when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
		when(repository.findCleanupCandidateIds(NOW, NOW.minusSeconds(86_400), 100)).thenReturn(List.of(5L));
		MediaAsset failed = pendingAsset(5L);
		failed.startProcessing();
		failed.recordProcessingObjectKeys("media/5/thumbnail.jpg", "media/5/detail.jpg");
		failed.markFailed("PROCESSING_FAILED");
		when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(failed));
		MediaAssetCleanupService service = new MediaAssetCleanupService(
			repository, storage, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(service.cleanupBatch()).isEqualTo(1);
		verify(storage).deleteObject("temporary/1/original");
		verify(storage).deleteObject("media/5/thumbnail.jpg");
		verify(storage).deleteObject("media/5/detail.jpg");
		verify(repository).delete(failed);
	}

	@Test
	void cleanup_must_not_let_one_hundred_permanent_failures_starve_the_next_candidate() {
		when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
		List<Long> blockedIds = LongStream.rangeClosed(1, 100).boxed().toList();
		when(repository.findCleanupCandidateIds(NOW, NOW.minusSeconds(86_400), 100))
			.thenReturn(blockedIds, blockedIds);
		for (Long id : blockedIds) {
			when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(pendingAssetWithKey(id)));
		}
		doThrow(new IllegalStateException("storage unavailable"))
			.when(storage).deleteObject(org.mockito.ArgumentMatchers.anyString());
		MediaAssetCleanupService service = new MediaAssetCleanupService(
			repository, storage, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(service.cleanupBatch()).isZero();
		assertThat(service.cleanupBatch()).isZero();

		verify(repository).findByIdForUpdate(101L);
	}

	@Test
	void cleanup_retries_a_transient_storage_failure_and_deletes_after_success() {
		when(transactionManager.getTransaction(org.mockito.ArgumentMatchers.any())).thenReturn(transactionStatus);
		when(repository.findCleanupCandidateIds(NOW, NOW.minusSeconds(86_400), 100))
			.thenReturn(List.of(101L), List.of(101L));
		MediaAsset candidate = pendingAssetWithKey(101L);
		when(repository.findByIdForUpdate(101L)).thenReturn(Optional.of(candidate));
		doThrow(new IllegalStateException("temporary storage failure"))
			.doNothing()
			.when(storage).deleteObject("temporary/101/original");
		MediaAssetCleanupService service = new MediaAssetCleanupService(
			repository, storage, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThat(service.cleanupBatch()).isZero();
		assertThat(service.cleanupBatch()).isEqualTo(1);

		verify(storage, org.mockito.Mockito.times(2)).deleteObject("temporary/101/original");
		verify(repository).delete(candidate);
	}

	private MediaAsset pendingAsset(Long id) {
		MediaAsset asset = MediaAsset.reserve(7L, 11L, "image/jpeg", 10, "a".repeat(64),
			"temporary/1/original", NOW.minusSeconds(1));
		ReflectionTestUtils.setField(asset, "id", id);
		return asset;
	}

	private MediaAsset pendingAssetWithKey(Long id) {
		MediaAsset asset = MediaAsset.reserve(7L, 11L, "image/jpeg", 10, "a".repeat(64),
			"temporary/" + id + "/original", NOW.minusSeconds(1));
		ReflectionTestUtils.setField(asset, "id", id);
		return asset;
	}

	private MediaAsset orphanedAsset(Long id) {
		MediaAsset asset = readyAsset(id);
		asset.markOrphaned(NOW.minusSeconds(86_401));
		return asset;
	}

	private MediaAsset readyAsset(Long id) {
		return readyAsset(id, NOW.plusSeconds(3600));
	}

	private MediaAsset readyAsset(Long id, Instant expiresAt) {
		MediaAsset asset = MediaAsset.reserve(7L, 11L, "image/jpeg", 10, "a".repeat(64),
			"temporary/3/original", expiresAt);
		ReflectionTestUtils.setField(asset, "id", id);
		asset.startProcessing();
		asset.complete("media/2/thumbnail.jpg", "media/2/detail.jpg", 100, 100, 20, "b".repeat(64));
		return asset;
	}
}
