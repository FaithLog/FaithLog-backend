package com.faithlog.poll.service.port;

import java.util.List;

public interface WeeklyMaterialMediaAttachmentPort {
	List<Long> findAttachedAssetIds(List<Long> assetIds);
}
