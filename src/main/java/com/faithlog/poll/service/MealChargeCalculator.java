package com.faithlog.poll.service;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.type.MealChargeCalculationType;
import org.springframework.stereotype.Component;

@Component
public class MealChargeCalculator {

	public MealChargeCalculation calculate(
		MealChargeCalculationType calculationType,
		long enteredAmount,
		int responseCount
	) {
		if (calculationType == null || responseCount <= 0) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_GROUPS);
		}
		if (enteredAmount <= 0) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_AMOUNT);
		}
		try {
			long amountPerMemberLong = calculationType == MealChargeCalculationType.PER_MEMBER
				? enteredAmount
				: ceilDivide(enteredAmount, responseCount);
			int amountPerMember = Math.toIntExact(amountPerMemberLong);
			if (amountPerMember <= 0) {
				throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_INVALID_AMOUNT);
			}
			long actualTotal = Math.multiplyExact(amountPerMemberLong, responseCount);
			long requestedTotal = calculationType == MealChargeCalculationType.PER_MEMBER
				? actualTotal
				: enteredAmount;
			long roundingAdjustment = Math.subtractExact(actualTotal, requestedTotal);
			return new MealChargeCalculation(
				amountPerMember,
				requestedTotal,
				actualTotal,
				roundingAdjustment
			);
		} catch (ArithmeticException exception) {
			throw new BusinessException(ErrorCode.MEAL_SETTLEMENT_AMOUNT_OVERFLOW);
		}
	}

	private long ceilDivide(long amount, int count) {
		long quotient = amount / count;
		return amount % count == 0 ? quotient : Math.addExact(quotient, 1L);
	}
}
