package com.faithlog.poll.controller.dto.request;

import com.faithlog.poll.service.command.CreatePollOptionCommand;

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
