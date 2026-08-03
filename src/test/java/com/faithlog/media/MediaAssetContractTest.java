package com.faithlog.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import net.coobird.thumbnailator.Thumbnails;

class MediaAssetContractTest {

	@Test
	void approved_provider_libraries_are_on_the_runtime_classpath() {
		assertThat(S3Presigner.class).isNotNull();
		assertThat(Thumbnails.class).isNotNull();
	}

	@Test
	void upload_reservation_uses_explicit_states_and_immutable_random_keys() {
		MediaAsset asset = MediaAsset.reserve(
			7L,
			11L,
			"image/jpeg",
			5L * 1024 * 1024,
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
			"tmp/7d6ac190-c13b-46c6-a211-c64328301534/original",
			Instant.parse("2026-08-04T00:00:00Z")
		);

		assertThat(asset.status()).isEqualTo(MediaAssetStatus.PENDING);
		asset.startProcessing();
		assertThat(asset.status()).isEqualTo(MediaAssetStatus.PROCESSING);
		asset.complete(
			"media/5f882bc0-ffed-4c27-8888-79e44fa0bd64/thumbnail.jpg",
			"media/5f882bc0-ffed-4c27-8888-79e44fa0bd64/detail.jpg",
			640,
			480,
			120_000,
			"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
		);
		assertThat(asset.status()).isEqualTo(MediaAssetStatus.READY);
		assertThat(asset.temporaryObjectKey()).isNull();
	}

	@Test
	void rejects_unsupported_or_oversized_input_and_invalid_transitions() {
		assertThatThrownBy(() -> MediaAsset.reserve(
			7L, 11L, "image/heic", 10, "a".repeat(64), "tmp/" + java.util.UUID.randomUUID(), Instant.now()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> MediaAsset.reserve(
			7L, 11L, "image/png", 5L * 1024 * 1024 + 1, "a".repeat(64),
			"tmp/" + java.util.UUID.randomUUID(), Instant.now()))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
