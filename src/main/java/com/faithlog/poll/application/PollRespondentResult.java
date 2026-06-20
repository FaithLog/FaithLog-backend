package com.faithlog.poll.application;

public record PollRespondentResult(
	Long userId,
	String name,
	String email
) {
}
