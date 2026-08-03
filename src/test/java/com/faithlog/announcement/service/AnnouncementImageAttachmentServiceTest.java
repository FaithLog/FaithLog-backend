package com.faithlog.announcement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
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
		when(images.existsByMediaAssetIdAndAnnouncementIdNot(31L, 101L)).thenReturn(true);

		assertThatThrownBy(() -> service.replace(101L, 7L, 11L, List.of(31L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_STATE_CONFLICT));

		verify(images, never()).deleteByAnnouncementId(101L);
	}

	private MediaAsset readyAsset(Long id, Long campusId, Long ownerId) {
		MediaAsset asset = MediaAsset.reserve(campusId, ownerId, "image/jpeg", 10, "a".repeat(64),
			"temporary/asset/original", Instant.parse("2026-08-04T00:00:00Z"));
		ReflectionTestUtils.setField(asset, "id", id);
		asset.startProcessing();
		asset.complete("media/a/thumbnail.jpg", "media/a/detail.jpg", 100, 100, 20, "b".repeat(64));
		return asset;
	}
}
