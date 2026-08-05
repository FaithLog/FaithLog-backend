package com.faithlog.announcement.service.command;

import java.time.Instant;
import java.util.List;

public record CreateAnnouncementCommand(
	Long campusId,
	Long requesterId,
	Long categoryId,
	String title,
	String content,
	boolean pinned,
	Instant publishAt,
	List<Long> imageAssetIds,
	List<Long> documentAssetIds
) {
	public CreateAnnouncementCommand(Long campusId, Long requesterId, Long categoryId, String title, String content,
		boolean pinned, Instant publishAt) {
		this(campusId, requesterId, categoryId, title, content, pinned, publishAt, List.of(), List.of());
	}
	public CreateAnnouncementCommand {
		imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds);
		documentAssetIds = documentAssetIds == null ? List.of() : List.copyOf(documentAssetIds);
	}
}
