package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.PollRespondentResult;

public record PollRespondentResponse(
	Long userId,
	String name,
	String email
) {

	public static PollRespondentResponse from(PollRespondentResult result) {
		return new PollRespondentResponse(result.userId(), result.name(), result.email());
	}
}
