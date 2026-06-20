package com.faithlog.poll.application;

public record CreatePollOptionCommand(
	String content,
	Long menuId,
	Integer priceAmount,
	int sortOrder
) {
}
