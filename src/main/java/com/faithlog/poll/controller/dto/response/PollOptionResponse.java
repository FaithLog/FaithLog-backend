package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.PollOptionResult;

public record PollOptionResponse(
	Long id,
	String content,
	String composeMenuCode,
	int priceAmount,
	int sortOrder,
	boolean userAdded
) {

	public static PollOptionResponse from(PollOptionResult result) {
		return new PollOptionResponse(
			result.id(),
			result.content(),
			result.composeMenuCode(),
			result.priceAmount(),
			result.sortOrder(),
			result.userAdded()
		);
	}
}
