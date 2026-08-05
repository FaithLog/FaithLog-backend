package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.announcement.domain.entity.AnnouncementImage;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementDocumentRepository;
import com.faithlog.announcement.service.port.PollMediaAttachmentPort;
import com.faithlog.announcement.service.port.WeeklyMaterialMediaAttachmentPort;
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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnnouncementImageAttachmentServiceTest {

	@Mock private AnnouncementImageRepository images;
	@Mock private AnnouncementDocumentRepository documents;
	@Mock private MediaAssetRepositoryPort assets;
	@Mock private PollMediaAttachmentPort pollImages;
	@Mock private WeeklyMaterialMediaAttachmentPort weeklyAttachments;
	private AnnouncementImageAttachmentService service;

	@BeforeEach
	void setUp() {
		service = new AnnouncementImageAttachmentService(images, documents, assets, pollImages, weeklyAttachments);
	}

	@Test
	void legacyWeeklyImageConflictIsRejectedAfterMediaLock() {
		MediaAsset asset = readyAsset(31L, 7L, 11L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L))).thenReturn(List.of(asset));
		when(weeklyAttachments.findAttachedAssetIds(List.of(31L))).thenReturn(List.of(31L));

		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_STATE_CONFLICT));
		verify(images, never()).deleteByAnnouncementId(101L);
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
	void ready_asset_already_attached_to_a_poll_is_rejected_before_replacement() {
		MediaAsset asset = readyAsset(31L, 7L, 11L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L))).thenReturn(List.of(asset));
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(31L))).thenReturn(List.of());
		when(pollImages.findAttachedAssetIds(List.of(31L))).thenReturn(List.of(31L));

		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_STATE_CONFLICT));

		verify(images, never()).deleteByAnnouncementId(101L);
	}

	@Test
	void co_manager_can_keep_an_existing_ready_asset_owned_by_the_original_manager() {
		MediaAsset originalManagerAsset = readyAsset(31L, 7L, 11L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L))
			.thenReturn(List.of(AnnouncementImage.create(7L, 101L, 31L, 0)));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L)))
			.thenReturn(List.of(originalManagerAsset));
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(31L))).thenReturn(List.of());

		service.replace(101L, 7L, 22L, List.of(31L));

		assertThat(originalManagerAsset.status()).isEqualTo(com.faithlog.media.domain.type.MediaAssetStatus.READY);
		verify(assets, never()).findByCampusIdAndIdForUpdate(7L, 31L);
		verify(images).save(org.mockito.ArgumentMatchers.argThat(image ->
			image.mediaAssetId().equals(31L) && image.displayOrder() == 0));
	}

	@Test
	void co_manager_can_reorder_existing_ready_assets_owned_by_another_manager() {
		MediaAsset first = readyAsset(31L, 7L, 11L);
		MediaAsset second = readyAsset(32L, 7L, 11L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of(
			AnnouncementImage.create(7L, 101L, 31L, 0),
			AnnouncementImage.create(7L, 101L, 32L, 1)
		));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L, 32L))).thenReturn(List.of(first, second));
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(31L, 32L))).thenReturn(List.of());

		service.replace(101L, 7L, 22L, List.of(32L, 31L));

		ArgumentCaptor<AnnouncementImage> saved = ArgumentCaptor.forClass(AnnouncementImage.class);
		verify(images, times(2)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(AnnouncementImage::mediaAssetId).containsExactly(32L, 31L);
		assertThat(saved.getAllValues()).extracting(AnnouncementImage::displayOrder).containsExactly(0, 1);
	}

	@Test
	void co_manager_can_mix_an_existing_asset_with_a_new_ready_asset_they_own() {
		MediaAsset existing = readyAsset(31L, 7L, 11L);
		MediaAsset newlyOwned = readyAsset(32L, 7L, 22L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L))
			.thenReturn(List.of(AnnouncementImage.create(7L, 101L, 31L, 0)));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L, 32L)))
			.thenReturn(List.of(existing, newlyOwned));
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(31L, 32L))).thenReturn(List.of());

		service.replace(101L, 7L, 22L, List.of(31L, 32L));

		verify(images, times(2)).save(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void removing_an_existing_asset_orphans_only_the_removed_asset() {
		MediaAsset removed = readyAsset(31L, 7L, 11L);
		MediaAsset retained = readyAsset(32L, 7L, 11L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of(
			AnnouncementImage.create(7L, 101L, 31L, 0),
			AnnouncementImage.create(7L, 101L, 32L, 1)
		));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(32L))).thenReturn(List.of(retained));
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(32L))).thenReturn(List.of());
		when(assets.findByCampusIdAndIdForUpdate(7L, 31L)).thenReturn(java.util.Optional.of(removed));

		service.replace(101L, 7L, 22L, List.of(32L));

		assertThat(removed.status()).isEqualTo(com.faithlog.media.domain.type.MediaAssetStatus.ORPHANED);
		assertThat(retained.status()).isEqualTo(com.faithlog.media.domain.type.MediaAssetStatus.READY);
	}

	@Test
	void co_manager_cannot_add_an_unattached_ready_asset_owned_by_another_manager() {
		MediaAsset otherManagerAsset = readyAsset(33L, 7L, 11L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of());
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(33L))).thenReturn(List.of(otherManagerAsset));
		when(images.findAttachedAssetIdsForOtherAnnouncements(101L, List.of(33L))).thenReturn(List.of());

		assertThatThrownBy(() -> service.replace(101L, 7L, 22L, List.of(33L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_INVALID));

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

	@Test
	void orphanAll_locks_existing_images_by_stable_asset_order_then_orphans_and_deletes_links() {
		MediaAsset first = readyAsset(31L, 7L, 11L);
		MediaAsset second = readyAsset(32L, 7L, 11L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L)).thenReturn(List.of(
			AnnouncementImage.create(7L, 101L, 32L, 0),
			AnnouncementImage.create(7L, 101L, 31L, 1)
		));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L, 32L))).thenReturn(List.of(first, second));

		service.orphanAll(101L, 7L);

		verify(assets).findByCampusIdAndIdInForUpdate(7L, List.of(31L, 32L));
		assertThat(first.status()).isEqualTo(com.faithlog.media.domain.type.MediaAssetStatus.ORPHANED);
		assertThat(second.status()).isEqualTo(com.faithlog.media.domain.type.MediaAssetStatus.ORPHANED);
		var ordered = inOrder(images);
		ordered.verify(images).deleteByAnnouncementId(101L);
		ordered.verify(images).flush();
	}

	@Test
	void orphanAll_rejects_missing_or_non_ready_image_without_deleting_links() {
		MediaAsset pending = MediaAsset.reserve(7L, 11L, "image/jpeg", 10, "a".repeat(64),
			"temporary/pending/original", Instant.parse("2026-08-04T00:00:00Z"));
		ReflectionTestUtils.setField(pending, "id", 31L);
		when(images.findByAnnouncementIdOrderByDisplayOrderAscIdAsc(101L))
			.thenReturn(List.of(AnnouncementImage.create(7L, 101L, 31L, 0)));
		when(assets.findByCampusIdAndIdInForUpdate(7L, List.of(31L))).thenReturn(List.of(pending));

		assertThatThrownBy(() -> service.orphanAll(101L, 7L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_INVALID));

		verify(images, never()).deleteByAnnouncementId(101L);
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
