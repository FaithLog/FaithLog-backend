package com.faithlog.devotion.controller.dto.request;

import com.faithlog.devotion.service.command.UpdatePenaltyRuleCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record UpdatePenaltyRuleRequest(
	@NotNull Integer requiredCount,
	@NotNull Integer baseAmount,
	@NotNull Integer amountPerUnit,
	@NotNull Boolean isActive
) {

	public UpdatePenaltyRuleCommand toCommand(Long ruleId, AuthenticatedUser authenticatedUser) {
		return new UpdatePenaltyRuleCommand(
			ruleId,
			authenticatedUser.userId(),
			requiredCount,
			baseAmount,
			amountPerUnit,
			isActive
		);
	}
}
