package com.faithlog.devotion.application;

import com.faithlog.devotion.domain.PenaltyCalculationType;
import com.faithlog.devotion.domain.PenaltyRuleType;

public record CreatePenaltyRuleCommand(
	Long campusId,
	Long requesterId,
	PenaltyRuleType ruleType,
	PenaltyCalculationType calculationType,
	int requiredCount,
	int baseAmount,
	int amountPerUnit
) {
}
