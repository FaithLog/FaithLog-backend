package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.PollOptionResult;

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
