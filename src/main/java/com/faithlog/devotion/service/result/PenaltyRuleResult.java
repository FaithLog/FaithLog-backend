package com.faithlog.devotion.service.result;

import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.entity.PenaltyRule;
import com.faithlog.devotion.domain.type.PenaltyRuleType;

public record PenaltyRuleResult(
	Long id,
	Long campusId,
	PenaltyRuleType ruleType,
	PenaltyCalculationType calculationType,
	int requiredCount,
	int baseAmount,
	int amountPerUnit,
	boolean isActive
) {

	public static PenaltyRuleResult from(PenaltyRule rule) {
		return new PenaltyRuleResult(
			rule.id(),
			rule.campusId(),
			rule.ruleType(),
			rule.calculationType(),
			rule.requiredCount(),
			rule.baseAmount(),
			rule.amountPerUnit(),
			rule.isActive()
		);
	}
}
