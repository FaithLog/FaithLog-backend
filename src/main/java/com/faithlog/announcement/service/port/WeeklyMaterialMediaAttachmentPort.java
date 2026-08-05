package com.faithlog.announcement.service.port;

import java.util.List;

public interface WeeklyMaterialMediaAttachmentPort {
	List<Long> findAttachedAssetIds(List<Long> assetIds);
}
