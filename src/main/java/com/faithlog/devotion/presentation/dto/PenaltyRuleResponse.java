package com.faithlog.devotion.presentation.dto;

import com.faithlog.devotion.application.PenaltyRuleResult;
import com.faithlog.devotion.domain.PenaltyCalculationType;
import com.faithlog.devotion.domain.PenaltyRuleType;

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
