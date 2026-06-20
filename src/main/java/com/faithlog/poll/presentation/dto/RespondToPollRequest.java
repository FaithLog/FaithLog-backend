package com.faithlog.poll.presentation.dto;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.application.RespondToPollCommand;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RespondToPollRequest(
	@NotEmpty List<Long> optionIds,
	String memo
) {

	public RespondToPollCommand toCommand(Long campusId, Long pollId, AuthenticatedUser authenticatedUser) {
		return new RespondToPollCommand(campusId, pollId, authenticatedUser.userId(), optionIds, memo);
	}
}
