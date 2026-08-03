package com.faithlog.announcement.service.command;

import java.time.Instant;

public record UpdateAnnouncementCommand(
	Long campusId,
	Long announcementId,
	Long requesterId,
	Long categoryId,
	String title,
	String content,
	boolean pinned,
	Instant publishAt
) {
}
