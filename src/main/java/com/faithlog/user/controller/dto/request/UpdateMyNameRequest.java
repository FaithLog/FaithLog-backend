package com.faithlog.user.controller.dto.request;

import com.faithlog.user.service.command.UpdateMyNameCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMyNameRequest(
	@NotBlank
	@Size(max = 100)
	String name
) {

	public UpdateMyNameRequest {
		name = name == null ? null : name.trim();
	}

	public UpdateMyNameCommand toCommand(Long userId) {
		return new UpdateMyNameCommand(userId, name);
	}
}
