package com.faithlog.announcement.controller.dto.request;

import com.faithlog.announcement.service.command.CreateAnnouncementCommand;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record CreateAnnouncementRequest(
	@NotNull @Positive Long categoryId,
	@NotBlank @Size(max = 100) String title,
	@NotBlank @Size(max = 5000) String content,
	boolean isPinned,
	@Future Instant publishAt,
	List<@Positive Long> imageAssetIds
) {
	public CreateAnnouncementRequest(Long categoryId, String title, String content, boolean isPinned, Instant publishAt) {
		this(categoryId, title, content, isPinned, publishAt, List.of());
	}
	public CreateAnnouncementRequest {
		title = title == null ? null : title.trim();
		content = content == null ? null : content.trim();
	}

	public CreateAnnouncementCommand toCommand(Long campusId, Long requesterId) {
		return new CreateAnnouncementCommand(campusId, requesterId, categoryId, title, content, isPinned, publishAt,
			imageAssetIds == null ? List.of() : imageAssetIds);
	}
}
