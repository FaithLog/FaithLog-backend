package com.faithlog.media.service.port;

import java.util.List;
import java.util.Set;

public interface PollMediaAccessPort {

	boolean canUpload(Long campusId, Long requesterId);

	Set<Long> readableAttachedAssetIds(Long campusId, Long requesterId, List<Long> assetIds);
}
