package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.CreatePollTemplateOptionCommand;

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
