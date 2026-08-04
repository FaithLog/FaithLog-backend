package com.faithlog.poll.service.port;

import java.util.List;

public interface AnnouncementMediaAttachmentPort {

	List<Long> findAttachedAssetIds(List<Long> assetIds);
}
