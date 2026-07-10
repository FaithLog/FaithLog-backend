package com.faithlog.poll.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.command.RespondToPollCommand;
import java.util.List;

public record RespondToPollRequest(
	List<Long> optionIds,
	String memo
) {

	public RespondToPollCommand toCommand(Long campusId, Long pollId, AuthenticatedUser authenticatedUser) {
		return new RespondToPollCommand(campusId, pollId, authenticatedUser.userId(), optionIds, memo);
	}
}
