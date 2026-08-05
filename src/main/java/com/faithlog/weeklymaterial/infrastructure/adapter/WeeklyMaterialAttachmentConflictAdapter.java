package com.faithlog.weeklymaterial.infrastructure.adapter;

import com.faithlog.announcement.infrastructure.repository.AnnouncementDocumentRepository;
import com.faithlog.announcement.infrastructure.repository.AnnouncementImageRepository;
import com.faithlog.poll.infrastructure.repository.PollDocumentRepository;
import com.faithlog.poll.infrastructure.repository.PollImageRepository;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialAttachmentConflictPort;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class WeeklyMaterialAttachmentConflictAdapter implements WeeklyMaterialAttachmentConflictPort {
	private final AnnouncementDocumentRepository announcementDocuments;
	private final AnnouncementImageRepository announcementImages;
	private final PollDocumentRepository pollDocuments;
	private final PollImageRepository pollImages;

	public WeeklyMaterialAttachmentConflictAdapter(AnnouncementDocumentRepository announcementDocuments,
		AnnouncementImageRepository announcementImages, PollDocumentRepository pollDocuments,
		PollImageRepository pollImages) {
		this.announcementDocuments = announcementDocuments;
		this.announcementImages = announcementImages;
		this.pollDocuments = pollDocuments;
		this.pollImages = pollImages;
	}

	@Override
	public List<Long> findAttachedAssetIds(List<Long> assetIds) {
		return Stream.of(
			announcementDocuments.findAttachedAssetIds(assetIds),
			announcementImages.findAttachedAssetIds(assetIds),
			pollDocuments.findAttachedAssetIds(assetIds),
			pollImages.findAttachedAssetIds(assetIds)
		).flatMap(List::stream).distinct().sorted().toList();
	}
}
