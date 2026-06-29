package com.faithlog.poll.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.application.AddPollOptionCommand;
import jakarta.validation.constraints.NotBlank;

public record AddPollOptionRequest(
	@NotBlank String content
) {

	public AddPollOptionCommand toCommand(Long campusId, Long pollId, AuthenticatedUser authenticatedUser) {
		return new AddPollOptionCommand(campusId, pollId, authenticatedUser.userId(), content);
	}
}
