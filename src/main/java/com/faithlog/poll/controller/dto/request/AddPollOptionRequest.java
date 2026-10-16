package com.faithlog.poll.controller.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.faithlog.poll.controller.json.PollPriceAmountDeserializer;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.command.AddPollOptionCommand;

public record AddPollOptionRequest(
	String content,
	Long menuId,
	@JsonDeserialize(using = PollPriceAmountDeserializer.class) Integer priceAmount
) {

	public AddPollOptionCommand toCommand(Long campusId, Long pollId, AuthenticatedUser authenticatedUser) {
		return new AddPollOptionCommand(campusId, pollId, authenticatedUser.userId(), content, menuId, priceAmount);
	}
}
