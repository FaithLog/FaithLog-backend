package com.faithlog.weeklymaterial.service.port;

import java.util.List;

public interface WeeklyMaterialAttachmentConflictPort {
	List<Long> findAttachedAssetIds(List<Long> assetIds);
}
