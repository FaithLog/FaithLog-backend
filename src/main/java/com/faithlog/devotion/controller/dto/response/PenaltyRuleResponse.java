package com.faithlog.devotion.controller.dto.response;

import com.faithlog.devotion.service.result.PenaltyRuleResult;
import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.type.PenaltyRuleType;

public record PenaltyRuleResponse(
	Long id,
	PenaltyRuleType ruleType,
	PenaltyCalculationType calculationType,
	int requiredCount,
	int baseAmount,
	int amountPerUnit,
	boolean isActive
) {

	public static PenaltyRuleResponse from(PenaltyRuleResult result) {
		return new PenaltyRuleResponse(
			result.id(),
			result.ruleType(),
			result.calculationType(),
			result.requiredCount(),
			result.baseAmount(),
			result.amountPerUnit(),
			result.isActive()
		);
	}
}
