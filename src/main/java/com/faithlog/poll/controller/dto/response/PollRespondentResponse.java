package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.PollRespondentResult;

public record PollRespondentResponse(
	Long userId,
	String name,
	String email
) {

	public static PollRespondentResponse from(PollRespondentResult result) {
		return new PollRespondentResponse(result.userId(), result.name(), result.email());
	}
}
