package com.faithlog.announcement.service.result;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import java.time.Instant;
import java.util.List;

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
	Instant updatedAt,
	List<Long> imageAssetIds,
	List<Long> documentAssetIds
) {
	public AnnouncementResult(Long id, Long campusId, AnnouncementCategoryResult category, Long authorId,
		String title, String content, boolean pinned, AnnouncementStatus status, Instant publishAt,
		Instant publishedAt, Instant createdAt, Instant updatedAt, List<Long> imageAssetIds) {
		this(id, campusId, category, authorId, title, content, pinned, status, publishAt, publishedAt,
			createdAt, updatedAt, imageAssetIds, List.of());
	}

	public AnnouncementResult {
		imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds);
		documentAssetIds = documentAssetIds == null ? List.of() : List.copyOf(documentAssetIds);
	}

	public static AnnouncementResult from(Announcement announcement, AnnouncementCategory category) {
		return from(announcement, category, List.of(), List.of());
	}

	public static AnnouncementResult from(
		Announcement announcement,
		AnnouncementCategory category,
		List<Long> imageAssetIds,
		List<Long> documentAssetIds
	) {
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
			announcement.updatedAt(),
			imageAssetIds,
			documentAssetIds
		);
	}
}
