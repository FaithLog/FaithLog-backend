package com.faithlog.media.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.media.domain.entity.MediaAsset;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MediaAssetCleanupRepositoryIntegrationTest {

	private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

	@Autowired
	private MediaAssetRepository repository;

	@Test
	void failed_first_page_backoff_exposes_the_next_candidate() {
		List<MediaAsset> assets = new ArrayList<>();
		for (int index = 1; index <= 101; index++) {
			assets.add(MediaAsset.reserve(
				7L, 11L, "image/jpeg", 10, "%064x".formatted(index),
				"temporary/repository/" + index + "/original", NOW.minusSeconds(1)));
		}
		repository.saveAllAndFlush(assets);
		List<Long> firstPage = repository.findCleanupCandidateIds(
			NOW, NOW.minus(Duration.ofHours(24)), 100);

		for (Long id : firstPage) {
			MediaAsset asset = repository.findByIdForUpdate(id).orElseThrow();
			String lease = "lease-" + id;
			assertThat(asset.claimCleanup(lease, NOW, Duration.ofMinutes(5))).isTrue();
			asset.recordCleanupFailure(
				lease, NOW, NOW.plus(Duration.ofMinutes(1)), "STORAGE_DELETE_FAILED");
		}
		repository.flush();

		assertThat(repository.findCleanupCandidateIds(
			NOW, NOW.minus(Duration.ofHours(24)), 100))
			.containsExactly(assets.get(100).id());
	}

	@Test
	void active_lease_is_excluded_and_expired_lease_is_recoverable() {
		MediaAsset asset = repository.saveAndFlush(MediaAsset.reserve(
			7L, 11L, "image/jpeg", 10, "f".repeat(64),
			"temporary/repository/lease/original", NOW.minusSeconds(1)));
		assertThat(asset.claimCleanup("lease-active", NOW, Duration.ofMinutes(5))).isTrue();
		repository.flush();

		assertThat(repository.findCleanupCandidateIds(
			NOW.plusSeconds(299), NOW.minus(Duration.ofHours(24)), 100)).isEmpty();
		assertThat(repository.findCleanupCandidateIds(
			NOW.plusSeconds(300), NOW.minus(Duration.ofHours(24)), 100)).containsExactly(asset.id());
	}

	@Test
	void stale_processing_is_selected_but_active_processing_is_not() {
		MediaAsset stale = processingAsset("stale");
		MediaAsset active = processingAsset("active");
		repository.saveAllAndFlush(List.of(stale, active));
		org.springframework.test.util.ReflectionTestUtils.setField(
			stale, "updatedAt", NOW.minus(Duration.ofHours(24)).minusNanos(1));
		org.springframework.test.util.ReflectionTestUtils.setField(
			active, "updatedAt", NOW.minus(Duration.ofHours(24)).plusNanos(1));
		repository.flush();

		assertThat(repository.findCleanupCandidateIds(
			NOW, NOW.minus(Duration.ofHours(24)), 100))
			.contains(stale.id())
			.doesNotContain(active.id());
	}

	private MediaAsset processingAsset(String suffix) {
		MediaAsset asset = MediaAsset.reserve(
			7L, 11L, "image/jpeg", 10, (suffix.equals("stale") ? "a" : "b").repeat(64),
			"temporary/repository/processing/" + suffix, NOW.minusSeconds(1));
		asset.startProcessing();
		asset.recordProcessingObjectKeys(
			"media/repository/processing/" + suffix + "/thumbnail.jpg",
			"media/repository/processing/" + suffix + "/detail.jpg");
		return asset;
	}
}
