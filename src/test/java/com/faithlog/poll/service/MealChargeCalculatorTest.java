package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.type.MealChargeCalculationType;
import org.junit.jupiter.api.Test;

class MealChargeCalculatorTest {

	private final MealChargeCalculator calculator = new MealChargeCalculator();

	@Test
	void calculates_per_member_and_group_total_with_exact_integer_rounding() {
		MealChargeCalculation perMember = calculator.calculate(MealChargeCalculationType.PER_MEMBER, 8000, 3);
		assertThat(perMember.amountPerMember()).isEqualTo(8000);
		assertThat(perMember.requestedTotalAmount()).isEqualTo(24000);
		assertThat(perMember.actualTotalAmount()).isEqualTo(24000);
		assertThat(perMember.roundingAdjustment()).isZero();

		MealChargeCalculation rounded = calculator.calculate(MealChargeCalculationType.GROUP_TOTAL, 10000, 3);
		assertThat(rounded.amountPerMember()).isEqualTo(3334);
		assertThat(rounded.requestedTotalAmount()).isEqualTo(10000);
		assertThat(rounded.actualTotalAmount()).isEqualTo(10002);
		assertThat(rounded.roundingAdjustment()).isEqualTo(2);

		MealChargeCalculation exact = calculator.calculate(MealChargeCalculationType.GROUP_TOTAL, 9000, 3);
		assertThat(exact.amountPerMember()).isEqualTo(3000);
		assertThat(exact.roundingAdjustment()).isZero();
		assertThat(calculator.calculate(MealChargeCalculationType.GROUP_TOTAL, 1, 1).amountPerMember()).isEqualTo(1);
	}

	@Test
	void rejects_non_positive_input_and_integer_overflow_as_business_errors() {
		assertThatThrownBy(() -> calculator.calculate(MealChargeCalculationType.PER_MEMBER, 0, 1))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEAL_SETTLEMENT_INVALID_AMOUNT));
		assertThatThrownBy(() -> calculator.calculate(MealChargeCalculationType.GROUP_TOTAL, 1000, 0))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEAL_SETTLEMENT_INVALID_GROUPS));
		assertThatThrownBy(() -> calculator.calculate(MealChargeCalculationType.GROUP_TOTAL, Long.MAX_VALUE, 2))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEAL_SETTLEMENT_AMOUNT_OVERFLOW));
	}
}
