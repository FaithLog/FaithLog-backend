package com.faithlog.announcement.service.command;

import java.time.Instant;
import java.util.List;

public record UpdateAnnouncementCommand(
	Long campusId,
	Long announcementId,
	Long requesterId,
	Long categoryId,
	String title,
	String content,
	boolean pinned,
	Instant publishAt,
	List<Long> imageAssetIds,
	List<Long> documentAssetIds
) {
	public UpdateAnnouncementCommand(Long campusId, Long announcementId, Long requesterId, Long categoryId,
		String title, String content, boolean pinned, Instant publishAt) {
		this(campusId, announcementId, requesterId, categoryId, title, content, pinned, publishAt, List.of(), List.of());
	}
	public UpdateAnnouncementCommand {
		imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds);
		documentAssetIds = documentAssetIds == null ? List.of() : List.copyOf(documentAssetIds);
	}
}
