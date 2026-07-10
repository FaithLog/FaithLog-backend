package com.faithlog.poll.service.command;

public record CreatePollTemplateOptionCommand(
	String content,
	Long menuId,
	Integer priceAmount,
	int sortOrder
) {
}
