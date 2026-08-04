package com.faithlog.announcement.infrastructure.adapter;

import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.poll.service.port.AnnouncementMediaAttachmentPort;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PollAnnouncementMediaAttachmentAdapter implements AnnouncementMediaAttachmentPort {

	private final AnnouncementImageRepository images;

	public PollAnnouncementMediaAttachmentAdapter(AnnouncementImageRepository images) {
		this.images = images;
	}

	@Override
	public List<Long> findAttachedAssetIds(List<Long> assetIds) {
		return images.findAttachedAssetIds(assetIds);
	}
}
