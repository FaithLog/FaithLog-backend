package com.faithlog.announcement.service.port;

import java.util.List;

public interface PollMediaAttachmentPort {

	List<Long> findAttachedAssetIds(List<Long> assetIds);
}
