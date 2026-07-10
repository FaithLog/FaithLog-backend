package com.faithlog.devotion.domain;

import com.faithlog.devotion.domain.entity.PenaltyRule;
import com.faithlog.devotion.domain.type.DevotionFineCalculationInput;
import com.faithlog.devotion.domain.type.DevotionFineCalculationItemResult;
import com.faithlog.devotion.domain.type.DevotionFineCalculationResult;
import com.faithlog.devotion.domain.type.PenaltyCalculationType;
import com.faithlog.devotion.domain.type.PenaltyRuleType;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DevotionFineCalculatorTest {

	private final DevotionFineCalculator calculator = new DevotionFineCalculator();

	@Test
	void calculates_missing_count_rules_and_saturday_late_rule_as_one_weekly_total() {
		List<PenaltyRule> rules = List.of(
			PenaltyRule.create(1L, PenaltyRuleType.QUIET_TIME, PenaltyCalculationType.MISSING_COUNT, 5, 0, 500),
			PenaltyRule.create(1L, PenaltyRuleType.PRAYER, PenaltyCalculationType.MISSING_COUNT, 5, 0, 500),
			PenaltyRule.create(1L, PenaltyRuleType.BIBLE_READING, PenaltyCalculationType.MISSING_COUNT, 5, 0, 300),
			PenaltyRule.create(1L, PenaltyRuleType.SATURDAY_LATE, PenaltyCalculationType.LATE_MINUTE, 0, 1000, 100)
		);

		DevotionFineCalculationResult result = calculator.calculate(new DevotionFineCalculationInput(
			3,
			5,
			2,
			5
		), rules);

		assertThat(result.totalAmount()).isEqualTo(3400);
		assertThat(result.items()).hasSize(4);
		assertThat(result.items())
			.filteredOn(item -> item.ruleType() == PenaltyRuleType.QUIET_TIME)
			.singleElement()
			.satisfies(item -> {
				assertThat(item.actualCount()).isEqualTo(3);
				assertThat(item.chargeUnitCount()).isEqualTo(2);
				assertThat(item.amount()).isEqualTo(1000);
			});
		assertThat(result.items())
			.filteredOn(item -> item.ruleType() == PenaltyRuleType.PRAYER)
			.singleElement()
			.satisfies(item -> {
				assertThat(item.actualCount()).isEqualTo(5);
				assertThat(item.chargeUnitCount()).isZero();
				assertThat(item.amount()).isZero();
			});
		assertThat(result.items())
			.filteredOn(item -> item.ruleType() == PenaltyRuleType.BIBLE_READING)
			.singleElement()
			.satisfies(item -> {
				assertThat(item.actualCount()).isEqualTo(2);
				assertThat(item.chargeUnitCount()).isEqualTo(3);
				assertThat(item.amount()).isEqualTo(900);
			});
		assertThat(result.items())
			.filteredOn(item -> item.ruleType() == PenaltyRuleType.SATURDAY_LATE)
			.singleElement()
			.satisfies(item -> {
				assertThat(item.actualCount()).isEqualTo(5);
				assertThat(item.chargeUnitCount()).isEqualTo(5);
				assertThat(item.amount()).isEqualTo(1500);
			});
	}

	@Test
	void saturday_late_rule_charges_zero_when_late_minutes_are_zero() {
		PenaltyRule lateRule = PenaltyRule.create(
			1L,
			PenaltyRuleType.SATURDAY_LATE,
			PenaltyCalculationType.LATE_MINUTE,
			0,
			1000,
			100
		);

		DevotionFineCalculationResult result = calculator.calculate(new DevotionFineCalculationInput(
			5,
			5,
			5,
			0
		), List.of(lateRule));

		assertThat(result.totalAmount()).isZero();
		assertThat(result.items()).singleElement().satisfies(item -> {
			assertThat(item.actualCount()).isZero();
			assertThat(item.chargeUnitCount()).isZero();
			assertThat(item.amount()).isZero();
		});
	}

	@Test
	void ignores_inactive_rules_when_calculating_weekly_total() {
		PenaltyRule activeRule = PenaltyRule.create(
			1L,
			PenaltyRuleType.QUIET_TIME,
			PenaltyCalculationType.MISSING_COUNT,
			5,
			0,
			500
		);
		PenaltyRule inactiveRule = PenaltyRule.create(
			1L,
			PenaltyRuleType.PRAYER,
			PenaltyCalculationType.MISSING_COUNT,
			5,
			0,
			500
		);
		inactiveRule.deactivate();

		DevotionFineCalculationResult result = calculator.calculate(new DevotionFineCalculationInput(
			4,
			0,
			5,
			0
		), List.of(activeRule, inactiveRule));

		assertThat(result.totalAmount()).isEqualTo(500);
		assertThat(result.items()).extracting(DevotionFineCalculationItemResult::ruleType)
			.containsExactly(PenaltyRuleType.QUIET_TIME);
	}
}
