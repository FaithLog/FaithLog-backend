package com.faithlog.announcement.service.result;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import java.time.Instant;

public record AnnouncementResult(
	Long id,
	Long campusId,
	AnnouncementCategoryResult category,
	Long authorId,
	String title,
	String content,
	boolean pinned,
	AnnouncementStatus status,
	Instant publishAt,
	Instant publishedAt,
	Instant createdAt,
	Instant updatedAt
) {

	public static AnnouncementResult from(Announcement announcement, AnnouncementCategory category) {
		return new AnnouncementResult(
			announcement.id(),
			announcement.campusId(),
			AnnouncementCategoryResult.from(category),
			announcement.authorId(),
			announcement.title(),
			announcement.content(),
			announcement.isPinned(),
			announcement.status(),
			announcement.publishAt(),
			announcement.publishedAt(),
			announcement.createdAt(),
			announcement.updatedAt()
		);
	}
}
