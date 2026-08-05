package com.faithlog.poll.infrastructure.adapter;

import com.faithlog.announcement.service.port.PollMediaAttachmentPort;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.poll.infrastructure.repository.PollDocumentRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementPollMediaAttachmentAdapter implements PollMediaAttachmentPort {

	private final PollImageRepository images;
	private final PollDocumentRepository documents;

	public AnnouncementPollMediaAttachmentAdapter(PollImageRepository images, PollDocumentRepository documents) {
		this.images = images;
		this.documents = documents;
	}

	@Override
	public List<Long> findAttachedAssetIds(List<Long> assetIds) {
		var result = new java.util.ArrayList<>(images.findAttachedAssetIds(assetIds));
		result.addAll(documents.findAttachedAssetIds(assetIds));
		return result;
	}
}
