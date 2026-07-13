package com.faithlog.devotion.domain;

import com.faithlog.devotion.domain.entity.PenaltyRule;
import com.faithlog.devotion.domain.type.DevotionFineCalculationInput;
import com.faithlog.devotion.domain.type.DevotionFineCalculationItemResult;
import com.faithlog.devotion.domain.type.DevotionFineCalculationResult;
import com.faithlog.devotion.domain.type.PenaltyRuleType;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DevotionFineCalculator {

	public DevotionFineCalculationResult calculate(DevotionFineCalculationInput input, List<PenaltyRule> rules) {
		try {
			List<DevotionFineCalculationItemResult> items = rules.stream()
				.filter(PenaltyRule::isActive)
				.sorted(Comparator.comparing(PenaltyRule::ruleType))
				.map(rule -> calculateItem(input, rule))
				.toList();
			long totalAmount = 0L;
			for (DevotionFineCalculationItemResult item : items) {
				totalAmount = Math.addExact(totalAmount, item.amount());
			}
			return new DevotionFineCalculationResult(toChargeStorageAmount(totalAmount), items);
		} catch (ArithmeticException exception) {
			throw new BusinessException(ErrorCode.DEVOTION_FINE_AMOUNT_OUT_OF_RANGE);
		}
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
		long amount = Math.multiplyExact((long) missingCount, (long) rule.amountPerUnit());
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
		long amount = lateMinutes == 0
			? 0L
			: Math.addExact(
				(long) rule.baseAmount(),
				Math.multiplyExact((long) lateMinutes, (long) rule.amountPerUnit())
			);
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

	private int toChargeStorageAmount(long amount) {
		if (amount < 0 || amount > Integer.MAX_VALUE) {
			throw new BusinessException(ErrorCode.DEVOTION_FINE_AMOUNT_OUT_OF_RANGE);
		}
		return (int) amount;
	}
}
