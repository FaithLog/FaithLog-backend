package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnnouncementImageAttachmentServiceTest {

	@Mock private AnnouncementImageRepository images;
	@Mock private MediaAssetRepositoryPort assets;
	private AnnouncementImageAttachmentService service;

	@BeforeEach
	void setUp() {
		service = new AnnouncementImageAttachmentService(images, assets);
	}

	@Test
	void ready_asset_already_attached_to_another_announcement_is_rejected_before_replacement() {
		MediaAsset asset = readyAsset(31L, 7L, 11L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L))).thenReturn(List.of(asset));
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(31L))).thenReturn(List.of(31L));

		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_STATE_CONFLICT));

		verify(images, never()).deleteByAnnouncementId(101L);
	}

	@Test
	void validation_locks_and_checks_assets_in_ordered_batches_then_flushes_replacement_before_insert() {
		List<Long> requested = java.util.stream.LongStream.rangeClosed(1, 101).boxed()
			.sorted(java.util.Comparator.reverseOrder()).toList();
		List<Long> firstBatch = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();
		List<Long> secondBatch = List.of(101L);
		List<MediaAsset> firstAssets = firstBatch.stream().map(id -> readyAsset(id, 7L, 11L)).toList();
		List<MediaAsset> secondAssets = secondBatch.stream().map(id -> readyAsset(id, 7L, 11L)).toList();
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, firstBatch)).thenReturn(firstAssets);
		when(assets.findByCampusIdAndIdInForUpdate(7L, secondBatch)).thenReturn(secondAssets);
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, firstBatch)).thenReturn(List.of());
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, secondBatch)).thenReturn(List.of());

		service.replace(101L, 7L, 11L, requested);

		var ordered = inOrder(images);
		ordered.verify(images).deleteByAnnouncementId(101L);
		ordered.verify(images).flush();
		ordered.verify(images, org.mockito.Mockito.times(101)).save(org.mockito.ArgumentMatchers.any());
	}

	private MediaAsset readyAsset(Long id, Long campusId, Long ownerId) {
		MediaAsset asset = MediaAsset.reserve(campusId, ownerId, "image/jpeg", 10, "a".repeat(64),
			"temporary/" + id + "/original", Instant.parse("2026-08-04T00:00:00Z"));
		ReflectionTestUtils.setField(asset, "id", id);
		asset.startProcessing();
		asset.complete("media/" + id + "/thumbnail.jpg", "media/" + id + "/detail.jpg",
			100, 100, 20, "b".repeat(64));
		return asset;
	}
}
