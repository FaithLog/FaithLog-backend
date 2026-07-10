package com.faithlog.poll.service.result;

public record PollDetailResult(
	PollResult poll,
	PollResponseResult myResponse
) {
}
