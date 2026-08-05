package com.faithlog.announcement.controller.dto.response;

import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.result.AnnouncementResult;
import java.time.Instant;
import java.util.List;

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
	Instant updatedAt,
	List<Long> imageAssetIds,
	List<Long> documentAssetIds
) {
	public static AnnouncementResponse from(AnnouncementResult result) {
		return new AnnouncementResponse(
			result.id(), result.campusId(), AnnouncementCategoryResponse.from(result.category()), result.authorId(),
			result.title(), result.content(), result.pinned(), result.status(), result.publishAt(), result.publishedAt(),
			result.createdAt(), result.updatedAt(), result.imageAssetIds(), result.documentAssetIds());
	}
}
