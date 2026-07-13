package com.faithlog.poll.controller.dto.request;

import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.command.CreateMealPollChargesCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateMealPollChargesRequest(
	@NotNull Long paymentAccountId,
	@NotNull @Valid List<@NotNull CreateMealPollChargeGroupRequest> groups
) {

	public CreateMealPollChargesCommand toCommand(
		Long campusId,
		Long pollId,
		AuthenticatedUser authenticatedUser
	) {
		return new CreateMealPollChargesCommand(
			campusId,
			pollId,
			authenticatedUser.userId(),
			paymentAccountId,
			groups.stream().map(CreateMealPollChargeGroupRequest::toCommand).toList()
		);
	}
}
