package com.faithlog.poll.controller.dto.request;

import com.faithlog.poll.domain.type.MealChargeCalculationType;
import com.faithlog.poll.service.command.CreateMealPollChargeGroupCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMealPollChargeGroupRequest(
	@NotNull Long optionId,
	@NotNull MealChargeCalculationType calculationType,
	@Positive long enteredAmount
) {

	public CreateMealPollChargeGroupCommand toCommand() {
		return new CreateMealPollChargeGroupCommand(optionId, calculationType, enteredAmount);
	}
}
