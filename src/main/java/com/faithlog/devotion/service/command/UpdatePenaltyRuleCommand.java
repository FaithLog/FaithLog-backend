package com.faithlog.devotion.service.command;

public record UpdatePenaltyRuleCommand(
	Long ruleId,
	Long requesterId,
	int requiredCount,
	int baseAmount,
	int amountPerUnit,
	boolean isActive
) {
}
