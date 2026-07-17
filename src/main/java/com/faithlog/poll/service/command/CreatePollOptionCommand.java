package com.faithlog.poll.service.command;

public record CreatePollOptionCommand(
	String content,
	Long menuId,
	Integer priceAmount,
	int sortOrder
) {
}
