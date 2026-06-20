package com.faithlog.poll.application;

public record PollDetailResult(
	PollResult poll,
	PollResponseResult myResponse
) {
}
