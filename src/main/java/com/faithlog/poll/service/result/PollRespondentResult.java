package com.faithlog.poll.service.result;

public record PollRespondentResult(
	Long userId,
	String name,
	String email
) {
}
