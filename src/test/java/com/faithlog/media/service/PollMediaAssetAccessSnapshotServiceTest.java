package com.faithlog.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.port.MediaAssetRepositoryPort;
import com.faithlog.media.service.policy.MediaAssetAccessPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PollMediaAssetAccessSnapshotServiceTest {

	@Mock private MediaAssetRepositoryPort assets;
	@Mock private MediaAssetAccessPolicy accessPolicy;

	@Test
	void active_member_can_resolve_ready_asset_attached_to_visible_poll() {
		MediaAsset asset = readyAsset(31L, 7L, 11L);
		when(accessPolicy.readableAssetIds(7L, 12L, List.of(31L))).thenReturn(Set.of(31L));
		when(assets.findByIdIn(List.of(31L))).thenReturn(List.of(asset));

		var result = new MediaAssetAccessSnapshotService(assets, accessPolicy)
			.authorize(7L, 12L, List.of(31L));

		assertThat(result).extracting(MediaAssetAccessSnapshotService.AccessSnapshot::assetId)
			.containsExactly(31L);
	}

	@Test
	void poll_creator_can_preview_only_their_own_unattached_ready_asset() {
		MediaAsset own = readyAsset(31L, 7L, 11L);
		when(accessPolicy.readableAssetIds(7L, 11L, List.of(31L))).thenReturn(Set.of());
		when(accessPolicy.canPreviewOwnedPollAsset(7L, 11L)).thenReturn(true);
		when(assets.findByIdIn(List.of(31L))).thenReturn(List.of(own));

		assertThat(new MediaAssetAccessSnapshotService(assets, accessPolicy)
			.authorize(7L, 11L, List.of(31L))).hasSize(1);

		MediaAsset anotherOwners = readyAsset(32L, 7L, 13L);
		when(accessPolicy.readableAssetIds(7L, 11L, List.of(32L))).thenReturn(Set.of());
		when(assets.findByIdIn(List.of(32L))).thenReturn(List.of(anotherOwners));

		assertThatThrownBy(() -> new MediaAssetAccessSnapshotService(assets, accessPolicy)
			.authorize(7L, 11L, List.of(32L)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN));
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
