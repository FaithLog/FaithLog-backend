package com.faithlog.poll.service.result;

public record PollMissingMemberResult(
	Long userId,
	String name,
	String email
) {
}
