package com.faithlog.user.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailDispatchTaskRequest(
	@NotBlank
	@Pattern(regexp = "[A-Za-z0-9_-]{43,128}")
	String dispatchToken
) {
}
