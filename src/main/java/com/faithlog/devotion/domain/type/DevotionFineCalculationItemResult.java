package com.faithlog.devotion.domain.type;

public record DevotionFineCalculationItemResult(
	PenaltyRuleType ruleType,
	PenaltyCalculationType calculationType,
	int requiredCount,
	int actualCount,
	int chargeUnitCount,
	int baseAmount,
	int amountPerUnit,
	int amount
) {
}
