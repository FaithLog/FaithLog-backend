package com.faithlog.poll.service;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;

public final class CoffeeOperationClassifier {

	private CoffeeOperationClassifier() {
	}

	public static boolean isCoffeeOperation(
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory
	) {
		return pollType == PollType.COFFEE
			|| chargeGenerationType == ChargeGenerationType.OPTION_PRICE
			|| paymentCategory == PaymentCategory.COFFEE;
	}

	public static void requireConsistentConfiguration(
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory
	) {
		if (!isConsistentConfiguration(pollType, chargeGenerationType, paymentCategory)) {
			throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED, "커피 투표 설정이 올바르지 않습니다.");
		}
	}

	public static boolean isConsistentConfiguration(
		PollType pollType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory
	) {
		return !isCoffeeOperation(pollType, chargeGenerationType, paymentCategory)
			|| (pollType == PollType.COFFEE
				&& chargeGenerationType == ChargeGenerationType.OPTION_PRICE
				&& paymentCategory == PaymentCategory.COFFEE);
	}
}
