package com.faithlog.announcement.service.command;

import java.time.Instant;

public record CreateAnnouncementCommand(
	Long campusId,
	Long requesterId,
	Long categoryId,
	String title,
	String content,
	boolean pinned,
	Instant publishAt
) {
}
