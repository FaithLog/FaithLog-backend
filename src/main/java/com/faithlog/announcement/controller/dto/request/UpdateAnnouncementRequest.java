package com.faithlog.announcement.controller.dto.request;

import com.faithlog.announcement.service.command.UpdateAnnouncementCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateAnnouncementRequest(
	@NotNull @Positive Long categoryId,
	@NotBlank @Size(max = 100) String title,
	@NotBlank @Size(max = 5000) String content,
	boolean isPinned,
	Instant publishAt
) {
	public UpdateAnnouncementRequest {
		title = title == null ? null : title.trim();
		content = content == null ? null : content.trim();
	}

	public UpdateAnnouncementCommand toCommand(Long campusId, Long announcementId, Long requesterId) {
		return new UpdateAnnouncementCommand(
			campusId, announcementId, requesterId, categoryId, title, content, isPinned, publishAt);
	}
}
