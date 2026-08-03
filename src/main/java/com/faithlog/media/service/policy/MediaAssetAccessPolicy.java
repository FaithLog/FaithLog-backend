package com.faithlog.media.service.policy;

import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.service.policy.PollMediaAccessPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MediaAssetAccessPolicy {

	private final AnnouncementAccessPolicy announcements;
	private final AnnouncementImageRepository announcementImages;
	private final PollMediaAccessPolicy polls;

	public MediaAssetAccessPolicy(
		AnnouncementAccessPolicy announcements,
		AnnouncementImageRepository announcementImages,
		PollMediaAccessPolicy polls
	) {
		this.announcements = announcements;
		this.announcementImages = announcementImages;
		this.polls = polls;
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
		HashSet<Long> readable = new HashSet<>(
			announcementImages.findPublishedAttachedAssetIds(campusId, assetIds));
		readable.addAll(polls.readableAttachedAssetIds(campusId, requesterId, assetIds));
		return Set.copyOf(readable);
	}

	public boolean canPreviewOwnedPollAsset(Long campusId, Long requesterId) {
		return polls.canUpload(campusId, requesterId);
	}

	private boolean isAnnouncementManager(Long campusId, Long requesterId) {
		try {
			announcements.requireManager(campusId, requesterId);
			return true;
		} catch (BusinessException exception) {
			if (exception.errorCode() != ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN) {
				throw exception;
			}
			return false;
		}
	}
}
