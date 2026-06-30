package com.faithlog.poll.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.application.AddPollOptionCommand;

public record AddPollOptionRequest(
	String content,
	Long menuId
) {

	public AddPollOptionCommand toCommand(Long campusId, Long pollId, AuthenticatedUser authenticatedUser) {
		return new AddPollOptionCommand(campusId, pollId, authenticatedUser.userId(), content, menuId);
	}
}
