package com.faithlog.devotion.service.command;

import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.type.PenaltyRuleType;

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
