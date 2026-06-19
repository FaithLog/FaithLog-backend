package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.CreatePollOptionCommand;

public record PollOptionRequest(
	String content,
	Long menuId,
	Integer priceAmount,
	int sortOrder
) {

	public CreatePollOptionCommand toCommand() {
		return new CreatePollOptionCommand(content, menuId, priceAmount, sortOrder);
	}
}
