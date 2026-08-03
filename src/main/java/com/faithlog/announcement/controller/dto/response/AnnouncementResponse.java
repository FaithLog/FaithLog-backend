package com.faithlog.announcement.controller.dto.response;

import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.result.AnnouncementResult;
import java.time.Instant;

public record AnnouncementResponse(
	Long id,
	Long campusId,
	AnnouncementCategoryResponse category,
	Long authorId,
	String title,
	String content,
	boolean isPinned,
	AnnouncementStatus status,
	Instant publishAt,
	Instant publishedAt,
	Instant createdAt,
	Instant updatedAt
) {
	public static AnnouncementResponse from(AnnouncementResult result) {
		return new AnnouncementResponse(
			result.id(), result.campusId(), AnnouncementCategoryResponse.from(result.category()), result.authorId(),
			result.title(), result.content(), result.pinned(), result.status(), result.publishAt(), result.publishedAt(),
			result.createdAt(), result.updatedAt());
	}
}
