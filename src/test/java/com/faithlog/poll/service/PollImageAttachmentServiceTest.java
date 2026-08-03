package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PollImageAttachmentServiceTest {

	@Mock private PollImageRepository images;
	@Mock private MediaAssetRepositoryPort assets;
	@Mock private AnnouncementImageRepository announcementImages;
	private PollImageAttachmentService service;

	@BeforeEach
	void setUp() {
		service = new PollImageAttachmentService(images, assets, announcementImages);
	}

	@Test
	void rejects_duplicate_asset_ids_before_repository_mutation() {
		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(31L, 31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_INVALID));

		verify(images, never()).deleteByPollId(101L);
	}

	@Test
	void rejects_ready_asset_owned_by_another_user() {
		when(images.findByPollIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L)))
			.thenReturn(List.of(readyAsset(31L, 7L, 12L)));
		when(images.findAttachedAssetIdsForOtherPolls(101L, List.of(31L))).thenReturn(List.of());
		when(announcementImages.findAttachedAssetIds(List.of(31L))).thenReturn(List.of());

		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_INVALID));

		verify(images, never()).deleteByPollId(101L);
	}

	@Test
	void rejects_asset_already_attached_to_an_announcement() {
		when(images.findByPollIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L)))
			.thenReturn(List.of(readyAsset(31L, 7L, 11L)));
		when(images.findAttachedAssetIdsForOtherPolls(101L, List.of(31L))).thenReturn(List.of());
		when(announcementImages.findAttachedAssetIds(List.of(31L))).thenReturn(List.of(31L));

		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_STATE_CONFLICT));

		verify(images, never()).deleteByPollId(101L);
	}

	@Test
	void validates_unbounded_product_list_in_safe_batches_and_preserves_requested_order() {
		List<Long> requested = java.util.stream.LongStream.rangeClosed(1, 101).boxed()
			.sorted(java.util.Comparator.reverseOrder()).toList();
		List<Long> firstBatch = java.util.stream.LongStream.rangeClosed(1, 100).boxed().toList();
		List<Long> secondBatch = List.of(101L);
		when(images.findByPollIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, firstBatch))
			.thenReturn(firstBatch.stream().map(id -> readyAsset(id, 7L, 11L)).toList());
		when(assets.findByCampusIdAndIdInForUpdate(7L, secondBatch))
			.thenReturn(secondBatch.stream().map(id -> readyAsset(id, 7L, 11L)).toList());
		when(images.findAttachedAssetIdsForOtherPolls(101L, firstBatch)).thenReturn(List.of());
		when(images.findAttachedAssetIdsForOtherPolls(101L, secondBatch)).thenReturn(List.of());
		when(announcementImages.findAttachedAssetIds(firstBatch)).thenReturn(List.of());
		when(announcementImages.findAttachedAssetIds(secondBatch)).thenReturn(List.of());

		service.replace(101L, 7L, 11L, requested);

		var ordered = inOrder(images);
		ordered.verify(images).deleteByPollId(101L);
		ordered.verify(images).flush();
		var captor = org.mockito.ArgumentCaptor.forClass(com.faithlog.poll.domain.entity.PollImage.class);
		verify(images, org.mockito.Mockito.times(101)).save(captor.capture());
		assertThat(captor.getAllValues()).extracting(com.faithlog.poll.domain.entity.PollImage::mediaAssetId)
			.containsExactlyElementsOf(requested);
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
