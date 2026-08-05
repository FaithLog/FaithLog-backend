package com.faithlog.media.service.port;

import java.util.List;
import java.util.Set;

public interface WeeklyMaterialMediaAccessPort {
	Set<Long> findActiveAttachedAssetIds(Long campusId, List<Long> assetIds);
}
