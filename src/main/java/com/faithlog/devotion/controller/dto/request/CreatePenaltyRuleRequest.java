package com.faithlog.devotion.controller.dto.request;

import com.faithlog.devotion.service.command.CreatePenaltyRuleCommand;
import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.type.PenaltyRuleType;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record CreatePenaltyRuleRequest(
	@NotNull PenaltyRuleType ruleType,
	@NotNull PenaltyCalculationType calculationType,
	@NotNull Integer requiredCount,
	@NotNull Integer baseAmount,
	@NotNull Integer amountPerUnit
) {

	public CreatePenaltyRuleCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new CreatePenaltyRuleCommand(
			campusId,
			authenticatedUser.userId(),
			ruleType,
			calculationType,
			requiredCount,
			baseAmount,
			amountPerUnit
		);
	}
}
