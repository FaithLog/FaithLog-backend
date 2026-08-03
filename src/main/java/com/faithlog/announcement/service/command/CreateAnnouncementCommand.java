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
	List<Long> imageAssetIds
) {
	public CreateAnnouncementCommand(Long campusId, Long requesterId, Long categoryId, String title, String content,
		boolean pinned, Instant publishAt) {
		this(campusId, requesterId, categoryId, title, content, pinned, publishAt, List.of());
	}
	public CreateAnnouncementCommand { imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds); }
}
