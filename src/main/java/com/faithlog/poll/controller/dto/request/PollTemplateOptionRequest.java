package com.faithlog.poll.controller.dto.request;

import com.faithlog.poll.service.command.CreatePollTemplateOptionCommand;

public record PollTemplateOptionRequest(
	String content,
	Long menuId,
	Integer priceAmount,
	int sortOrder
) {

	public CreatePollTemplateOptionCommand toCommand() {
		return new CreatePollTemplateOptionCommand(content, menuId, priceAmount, sortOrder);
	}
}
