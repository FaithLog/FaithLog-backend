package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.PollTemplateOptionResult;

public record PollTemplateOptionResponse(
	Long id,
	String content,
	String composeMenuCode,
	int priceAmount,
	int sortOrder
) {

	public static PollTemplateOptionResponse from(PollTemplateOptionResult result) {
		return new PollTemplateOptionResponse(
			result.id(),
			result.content(),
			result.composeMenuCode(),
			result.priceAmount(),
			result.sortOrder()
		);
	}
}
