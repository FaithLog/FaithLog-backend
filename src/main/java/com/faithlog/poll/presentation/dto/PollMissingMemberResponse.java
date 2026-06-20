package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.PollMissingMemberResult;

public record PollMissingMemberResponse(
	Long userId,
	String name,
	String email
) {

	public static PollMissingMemberResponse from(PollMissingMemberResult result) {
		return new PollMissingMemberResponse(result.userId(), result.name(), result.email());
	}
}
