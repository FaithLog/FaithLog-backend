package com.faithlog.poll.infrastructure.adapter;

import com.faithlog.announcement.service.port.PollMediaAttachmentPort;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementPollMediaAttachmentAdapter implements PollMediaAttachmentPort {

	private final PollImageRepository images;

	public AnnouncementPollMediaAttachmentAdapter(PollImageRepository images) {
		this.images = images;
	}

	@Override
	public List<Long> findAttachedAssetIds(List<Long> assetIds) {
		return images.findAttachedAssetIds(assetIds);
	}
}
