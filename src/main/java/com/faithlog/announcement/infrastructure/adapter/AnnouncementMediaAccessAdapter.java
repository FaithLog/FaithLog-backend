package com.faithlog.announcement.infrastructure.adapter;

import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.service.port.AnnouncementMediaAccessPort;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementMediaAccessAdapter implements AnnouncementMediaAccessPort {

	private final AnnouncementAccessPolicy accessPolicy;
	private final AnnouncementImageRepository images;

	public AnnouncementMediaAccessAdapter(
		AnnouncementAccessPolicy accessPolicy,
		AnnouncementImageRepository images
	) {
		this.accessPolicy = accessPolicy;
		this.images = images;
	}

	@Override
	public boolean canManage(Long campusId, Long requesterId) {
		try {
			accessPolicy.requireManager(campusId, requesterId);
			return true;
		} catch (BusinessException exception) {
			if (exception.errorCode() != ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN) {
				throw exception;
			}
			return false;
		}
	}

	@Override
	public void requireActiveMember(Long campusId, Long requesterId) {
		accessPolicy.requireActiveMember(campusId, requesterId);
	}

	@Override
	public Set<Long> findPublishedAttachedAssetIds(Long campusId, List<Long> assetIds) {
		return Set.copyOf(images.findPublishedAttachedAssetIds(campusId, assetIds));
	}
}
