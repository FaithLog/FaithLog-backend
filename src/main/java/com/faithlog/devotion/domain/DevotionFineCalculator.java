package com.faithlog.devotion.domain;

import java.util.Comparator;
import java.util.List;

public class DevotionFineCalculator {

	public DevotionFineCalculationResult calculate(DevotionFineCalculationInput input, List<PenaltyRule> rules) {
		List<DevotionFineCalculationItemResult> items = rules.stream()
			.filter(PenaltyRule::isActive)
			.sorted(Comparator.comparing(PenaltyRule::ruleType))
			.map(rule -> calculateItem(input, rule))
			.toList();
		int totalAmount = items.stream()
			.mapToInt(DevotionFineCalculationItemResult::amount)
			.sum();
		return new DevotionFineCalculationResult(totalAmount, items);
	}

	private DevotionFineCalculationItemResult calculateItem(DevotionFineCalculationInput input, PenaltyRule rule) {
		return switch (rule.calculationType()) {
			case MISSING_COUNT -> calculateMissingCount(input, rule);
			case LATE_MINUTE -> calculateLateMinute(input, rule);
		};
	}

	private DevotionFineCalculationItemResult calculateMissingCount(DevotionFineCalculationInput input, PenaltyRule rule) {
		int actualCount = checkedCount(input, rule.ruleType());
		int missingCount = Math.max(rule.requiredCount() - actualCount, 0);
		int amount = missingCount * rule.amountPerUnit();
		return new DevotionFineCalculationItemResult(
			rule.ruleType(),
			rule.calculationType(),
			rule.requiredCount(),
			actualCount,
			missingCount,
			rule.baseAmount(),
			rule.amountPerUnit(),
			amount
		);
	}

	private DevotionFineCalculationItemResult calculateLateMinute(DevotionFineCalculationInput input, PenaltyRule rule) {
		int lateMinutes = input.saturdayLateMinutes();
		int amount = lateMinutes == 0 ? 0 : rule.baseAmount() + lateMinutes * rule.amountPerUnit();
		return new DevotionFineCalculationItemResult(
			rule.ruleType(),
			rule.calculationType(),
			rule.requiredCount(),
			lateMinutes,
			lateMinutes,
			rule.baseAmount(),
			rule.amountPerUnit(),
			amount
		);
	}

	private int checkedCount(DevotionFineCalculationInput input, PenaltyRuleType ruleType) {
		return switch (ruleType) {
			case QUIET_TIME -> input.quietTimeCount();
			case PRAYER -> input.prayerCount();
			case BIBLE_READING -> input.bibleReadingCount();
			case SATURDAY_LATE -> input.saturdayLateMinutes();
		};
	}
}
