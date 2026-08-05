package com.faithlog.media.service.policy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.service.port.AnnouncementMediaAccessPort;
import com.faithlog.media.service.port.PollMediaAccessPort;
import com.faithlog.media.service.port.WeeklyMaterialMediaAccessPort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetAccessPolicy {

	private final AnnouncementMediaAccessPort announcements;
	private final PollMediaAccessPort polls;
	private final WeeklyMaterialMediaAccessPort weeklyMaterials;

	public MediaAssetAccessPolicy(
		AnnouncementMediaAccessPort announcements,
		PollMediaAccessPort polls,
		WeeklyMaterialMediaAccessPort weeklyMaterials
	) {
		this.announcements = announcements;
		this.polls = polls;
		this.weeklyMaterials = weeklyMaterials;
	}

	public void requireUploadPermission(Long campusId, Long requesterId) {
		if (!isAnnouncementManager(campusId, requesterId) && !polls.canUpload(campusId, requesterId)) {
			throw new BusinessException(ErrorCode.MEDIA_ASSET_ACCESS_FORBIDDEN);
		}
	}

	public Set<Long> readableAssetIds(Long campusId, Long requesterId, List<Long> assetIds) {
		if (isAnnouncementManager(campusId, requesterId)) {
			return Set.copyOf(assetIds);
		}
		announcements.requireActiveMember(campusId, requesterId);
		HashSet<Long> readable = new HashSet<>(announcements.findPublishedAttachedAssetIds(campusId, assetIds));
		readable.addAll(polls.readableAttachedAssetIds(campusId, requesterId, assetIds));
		readable.addAll(weeklyMaterials.findActiveAttachedAssetIds(campusId, assetIds));
		return Set.copyOf(readable);
	}

	public boolean canPreviewOwnedPollAsset(Long campusId, Long requesterId) {
		return polls.canUpload(campusId, requesterId);
	}

	private boolean isAnnouncementManager(Long campusId, Long requesterId) {
		return announcements.canManage(campusId, requesterId);
	}
}
