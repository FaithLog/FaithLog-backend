package com.faithlog.poll.application;

public record PollMissingMemberResult(
	Long userId,
	String name,
	String email
) {
}
