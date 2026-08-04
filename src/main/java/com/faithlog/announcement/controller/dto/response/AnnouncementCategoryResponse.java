package com.faithlog.announcement.controller.dto.response;

import com.faithlog.announcement.service.result.AnnouncementCategoryResult;
import java.time.Instant;

public record AnnouncementCategoryResponse(
	Long id,
	Long campusId,
	String name,
	String color,
	int displayOrder,
	boolean isActive,
	Instant createdAt,
	Instant updatedAt
) {
	public static AnnouncementCategoryResponse from(AnnouncementCategoryResult result) {
		return new AnnouncementCategoryResponse(
			result.id(), result.campusId(), result.name(), result.color(), result.displayOrder(), result.active(),
			result.createdAt(), result.updatedAt());
	}
}
