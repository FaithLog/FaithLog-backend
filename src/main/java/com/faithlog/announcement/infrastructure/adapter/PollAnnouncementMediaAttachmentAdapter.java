package com.faithlog.announcement.infrastructure.adapter;

import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementDocumentRepository;
import com.faithlog.poll.service.port.AnnouncementMediaAttachmentPort;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PollAnnouncementMediaAttachmentAdapter implements AnnouncementMediaAttachmentPort {

	private final AnnouncementImageRepository images;
	private final AnnouncementDocumentRepository documents;

	public PollAnnouncementMediaAttachmentAdapter(
		AnnouncementImageRepository images,
		AnnouncementDocumentRepository documents
	) {
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
