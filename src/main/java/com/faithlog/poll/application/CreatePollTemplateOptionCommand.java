package com.faithlog.poll.application;

public record CreatePollTemplateOptionCommand(
	String content,
	Long menuId,
	Integer priceAmount,
	int sortOrder
) {
}
