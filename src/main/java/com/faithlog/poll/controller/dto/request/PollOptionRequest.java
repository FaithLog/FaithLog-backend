package com.faithlog.poll.controller.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.faithlog.poll.controller.json.PollPriceAmountDeserializer;
import com.faithlog.poll.service.command.CreatePollOptionCommand;

public record PollOptionRequest(
	String content,
	Long menuId,
	@JsonDeserialize(using = PollPriceAmountDeserializer.class) Integer priceAmount,
	int sortOrder
) {

	public CreatePollOptionCommand toCommand() {
		return new CreatePollOptionCommand(content, menuId, priceAmount, sortOrder);
	}
}
