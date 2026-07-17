package com.faithlog.poll.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.command.AddPollOptionCommand;

public record AddPollOptionRequest(
	String content,
	Long menuId
) {

	public AddPollOptionCommand toCommand(Long campusId, Long pollId, AuthenticatedUser authenticatedUser) {
		return new AddPollOptionCommand(campusId, pollId, authenticatedUser.userId(), content, menuId);
	}
}
