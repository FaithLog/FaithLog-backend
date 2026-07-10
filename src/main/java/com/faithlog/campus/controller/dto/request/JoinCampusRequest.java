package com.faithlog.campus.controller.dto.request;

import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinCampusRequest(
	@NotBlank
	@Size(max = 50)
	String inviteCode
) {

	public JoinCampusCommand toCommand(AuthenticatedUser authenticatedUser) {
		return new JoinCampusCommand(authenticatedUser.userId(), inviteCode);
	}
}
