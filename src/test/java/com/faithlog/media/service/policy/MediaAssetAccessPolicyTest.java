package com.faithlog.media.service.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.service.port.AnnouncementMediaAccessPort;
import com.faithlog.media.service.port.PollMediaAccessPort;
import com.faithlog.media.service.port.WeeklyMaterialMediaAccessPort;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaAssetAccessPolicyTest {

	@Mock private AnnouncementMediaAccessPort announcements;
	@Mock private PollMediaAccessPort polls;
	@Mock private WeeklyMaterialMediaAccessPort weeklyMaterials;
	private MediaAssetAccessPolicy policy;

	@BeforeEach
	void setUp() {
		policy = new MediaAssetAccessPolicy(announcements, polls, weeklyMaterials);
	}

	@Test
	void announcement_manager_can_read_every_requested_asset() {
		when(announcements.canManage(7L, 11L)).thenReturn(true);

		assertThat(policy.readableAssetIds(7L, 11L, List.of(31L, 32L)))
			.containsExactlyInAnyOrder(31L, 32L);
	}

	@Test
	void active_member_reads_union_of_published_announcement_visible_poll_and_active_weekly_assets() {
		when(announcements.canManage(7L, 12L)).thenReturn(false);
		when(announcements.findPublishedAttachedAssetIds(7L, List.of(31L, 32L, 33L)))
			.thenReturn(Set.of(31L));
		when(polls.readableAttachedAssetIds(7L, 12L, List.of(31L, 32L, 33L)))
			.thenReturn(Set.of(32L));
		when(weeklyMaterials.findActiveAttachedAssetIds(7L, List.of(31L, 32L, 33L)))
			.thenReturn(Set.of(33L));

		assertThat(policy.readableAssetIds(7L, 12L, List.of(31L, 32L, 33L)))
			.containsExactlyInAnyOrder(31L, 32L, 33L);
		verify(announcements, org.mockito.Mockito.atLeastOnce()).requireActiveMember(7L, 12L);
	}

	@Test
	void poll_duty_upload_and_preview_permission_delegates_to_poll_port() {
		when(announcements.canManage(7L, 13L)).thenReturn(false);
		when(polls.canUpload(7L, 13L)).thenReturn(true);

		policy.requireUploadPermission(7L, 13L);
		assertThat(policy.canPreviewOwnedPollAsset(7L, 13L)).isTrue();
	}

	@Test
	void requester_without_announcement_or_poll_permission_is_rejected() {
		when(announcements.canManage(7L, 14L)).thenReturn(false);
		when(polls.canUpload(7L, 14L)).thenReturn(false);

		assertThatThrownBy(() -> policy.requireUploadPermission(7L, 14L))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN));
	}
}
