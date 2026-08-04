package com.faithlog.announcement.controller.dto.request;

import com.faithlog.announcement.service.command.CreateAnnouncementCategoryCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateAnnouncementCategoryRequest(
	@NotBlank @Size(max = 30) String name,
	@NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color,
	@PositiveOrZero int displayOrder
) {
	public CreateAnnouncementCategoryRequest {
		name = name == null ? null : name.trim();
		color = color == null ? null : color.trim();
	}

	public CreateAnnouncementCategoryCommand toCommand(Long campusId, Long requesterId) {
		return new CreateAnnouncementCategoryCommand(campusId, requesterId, name, color, displayOrder);
	}
}
