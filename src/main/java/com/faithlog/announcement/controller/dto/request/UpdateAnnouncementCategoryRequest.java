package com.faithlog.announcement.controller.dto.request;

import com.faithlog.announcement.service.command.UpdateAnnouncementCategoryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateAnnouncementCategoryRequest(
	@NotBlank @Size(max = 30) String name,
	@NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
	@PositiveOrZero int displayOrder
) {
	public UpdateAnnouncementCategoryRequest {
		name = name == null ? null : name.trim();
		color = color == null ? null : color.trim();
	}

	public UpdateAnnouncementCategoryCommand toCommand(Long campusId, Long categoryId, Long requesterId) {
		return new UpdateAnnouncementCategoryCommand(campusId, categoryId, requesterId, name, color, displayOrder);
	}
}
