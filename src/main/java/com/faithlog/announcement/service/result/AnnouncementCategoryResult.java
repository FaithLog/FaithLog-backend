package com.faithlog.announcement.service.result;

import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import java.time.Instant;

public record AnnouncementCategoryResult(
	Long id,
	Long campusId,
	String name,
	String color,
	int displayOrder,
	boolean active,
	Instant createdAt,
	Instant updatedAt
) {

	public static AnnouncementCategoryResult from(AnnouncementCategory category) {
		return new AnnouncementCategoryResult(
			category.id(),
			category.campusId(),
			category.name(),
			category.color(),
			category.displayOrder(),
			category.isActive(),
			category.createdAt(),
			category.updatedAt()
		);
	}
}
