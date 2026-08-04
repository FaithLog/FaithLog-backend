package com.faithlog.media.service.port;

import java.util.List;
import java.util.Set;

public interface AnnouncementMediaAccessPort {

	boolean canManage(Long campusId, Long requesterId);

	void requireActiveMember(Long campusId, Long requesterId);

	Set<Long> findPublishedAttachedAssetIds(Long campusId, List<Long> assetIds);
}
