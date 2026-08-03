package com.faithlog.media.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.infrastructure.repository.MediaAssetRepository;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class MediaAssetFinalizeCleanupConcurrencyIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
	private static final Long CAMPUS_ID = 7L;
	private static final Long OWNER_ID = 11L;

	@Autowired private MediaAssetRepository repository;
	@Autowired private PlatformTransactionManager transactionManager;

	@AfterEach
	void cleanDatabase() {
		repository.deleteAll();
	}

	@Test
	@Timeout(10)
	void finalize_lock_wins_and_cleanup_preserves_ready_variants() throws Exception {
		String temporaryKey = "temporary/concurrency/finalize-first/original";
		Long assetId = persistStaleProcessing(temporaryKey);
		CountDownLatch finalizeLocked = new CountDownLatch(1);
		CountDownLatch allowFinalizeCommit = new CountDownLatch(1);
		CountDownLatch cleanupAttemptingLock = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> finalize = executor.submit(() -> transaction().executeWithoutResult(status -> {
				MediaAsset asset = repository.findByCampusIdAndIdForUpdate(CAMPUS_ID, assetId).orElseThrow();
				finalizeLocked.countDown();
				await(allowFinalizeCommit);
				asset.complete(thumbnailKey(assetId), detailKey(assetId), 400, 300, 20, "b".repeat(64));
			}));
			await(finalizeLocked);

			Future<Boolean> cleanupClaim = executor.submit(() -> {
				cleanupAttemptingLock.countDown();
				return transaction().execute(status -> {
					MediaAsset asset = repository.findByIdForUpdate(assetId).orElseThrow();
					return asset.recoverStaleProcessingForCleanup(NOW.minus(Duration.ofHours(24)), NOW);
				});
			});
			await(cleanupAttemptingLock);
			allowFinalizeCommit.countDown();

			finalize.get(5, TimeUnit.SECONDS);
			assertThat(cleanupClaim.get(5, TimeUnit.SECONDS)).isFalse();
		} finally {
			executor.shutdownNow();
		}

		RecordingStorage storage = new RecordingStorage();
		MediaAssetCleanupService cleanup = cleanupService(storage);
		assertThat(cleanup.cleanupBatch()).isEqualTo(1);

		MediaAsset ready = repository.findById(assetId).orElseThrow();
		assertThat(ready.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(ready.temporaryObjectKey()).isNull();
		assertThat(ready.thumbnailObjectKey()).isEqualTo(thumbnailKey(assetId));
		assertThat(ready.detailObjectKey()).isEqualTo(detailKey(assetId));
		assertThat(storage.deletedKeys()).containsExactly(temporaryKey);
	}

	@Test
	@Timeout(10)
	void cleanup_lock_wins_and_finalize_cannot_overwrite_cleanup_owned_state() throws Exception {
		String temporaryKey = "temporary/concurrency/cleanup-first/original";
		Long assetId = persistStaleProcessing(temporaryKey);
		BlockingStorage storage = new BlockingStorage();
		MediaAssetCleanupService cleanup = cleanupService(storage);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Integer> cleanupResult = executor.submit(cleanup::cleanupBatch);
			await(storage.deleteStarted);

			MediaAsset claimed = repository.findById(assetId).orElseThrow();
			assertThat(claimed.status()).isEqualTo(MediaAssetStatus.FAILED);
			assertThat(claimed.cleanupLeaseToken()).isNotBlank();

			Future<Throwable> finalizeFailure = executor.submit(() -> {
				try {
					transaction().executeWithoutResult(status -> {
						MediaAsset asset = repository.findByCampusIdAndIdForUpdate(CAMPUS_ID, assetId).orElseThrow();
						asset.complete(thumbnailKey(assetId), detailKey(assetId), 400, 300, 20, "b".repeat(64));
					});
					return null;
				} catch (Throwable throwable) {
					return throwable;
				}
			});
			Throwable failure = finalizeFailure.get(5, TimeUnit.SECONDS);
			assertThat(failure).isInstanceOf(IllegalStateException.class);

			storage.allowDelete.countDown();
			assertThat(cleanupResult.get(5, TimeUnit.SECONDS)).isEqualTo(1);
		} finally {
			storage.allowDelete.countDown();
			executor.shutdownNow();
		}

		assertThat(repository.findById(assetId)).isEmpty();
		assertThat(storage.deletedKeys()).containsExactly(
			temporaryKey, thumbnailKey(assetId), detailKey(assetId));
	}

	private Long persistStaleProcessing(String temporaryKey) {
		return transaction().execute(status -> {
			MediaAsset asset = repository.saveAndFlush(MediaAsset.reserve(
				CAMPUS_ID, OWNER_ID, "image/jpeg", 10, "a".repeat(64),
				temporaryKey, NOW.minusSeconds(1)));
			asset.startProcessing();
			asset.recordProcessingObjectKeys(thumbnailKey(asset.id()), detailKey(asset.id()));
			ReflectionTestUtils.setField(asset, "updatedAt", NOW.minus(Duration.ofHours(24)).minusSeconds(1));
			return asset.id();
		});
	}

	private MediaAssetCleanupService cleanupService(MediaObjectStoragePort storage) {
		return new MediaAssetCleanupService(
			repository, storage, transactionManager, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private static String thumbnailKey(Long assetId) {
		return "media/concurrency/" + assetId + "/thumbnail.jpg";
	}

	private static String detailKey(Long assetId) {
		return "media/concurrency/" + assetId + "/detail.jpg";
	}

	private static void await(CountDownLatch latch) {
		try {
			assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AssertionError(exception);
		}
	}

	private static class RecordingStorage implements MediaObjectStoragePort {
		private final List<String> deletedKeys = new CopyOnWriteArrayList<>();

		@Override
		public PresignedUpload presignUpload(String objectKey, String contentType, long byteSize) {
			throw new UnsupportedOperationException();
		}

		@Override
		public StoredObject getObject(String objectKey, long maximumBytes) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void putObject(String objectKey, String contentType, byte[] content) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void deleteObject(String objectKey) {
			deletedKeys.add(objectKey);
		}

		@Override
		public URI presignDownload(String objectKey) {
			throw new UnsupportedOperationException();
		}

		List<String> deletedKeys() {
			return List.copyOf(deletedKeys);
		}
	}

	private static final class BlockingStorage extends RecordingStorage {
		private final CountDownLatch deleteStarted = new CountDownLatch(1);
		private final CountDownLatch allowDelete = new CountDownLatch(1);

		@Override
		public void deleteObject(String objectKey) {
			deleteStarted.countDown();
			await(allowDelete);
			super.deleteObject(objectKey);
		}
	}
}
