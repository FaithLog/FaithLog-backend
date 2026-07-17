package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.PollMissingMemberResult;

public record PollMissingMemberResponse(
	Long userId,
	String name,
	String email
) {

	public static PollMissingMemberResponse from(PollMissingMemberResult result) {
		return new PollMissingMemberResponse(result.userId(), result.name(), result.email());
	}
}
