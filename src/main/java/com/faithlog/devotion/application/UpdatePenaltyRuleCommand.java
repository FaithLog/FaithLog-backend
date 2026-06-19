package com.faithlog.devotion.application;

public record UpdatePenaltyRuleCommand(
	Long ruleId,
	Long requesterId,
	int requiredCount,
	int baseAmount,
	int amountPerUnit,
	boolean isActive
) {
}
