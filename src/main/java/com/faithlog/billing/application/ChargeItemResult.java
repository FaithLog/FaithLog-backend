package com.faithlog.billing.application;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import java.time.LocalDate;

public record ChargeItemResult(
	Long id,
	Long campusId,
	Long userId,
	PaymentCategory paymentCategory,
	Long paymentAccountId,
	String bankNameSnapshot,
	String accountNumberSnapshot,
	String accountHolderSnapshot,
	ChargeSourceType sourceType,
	Long sourceId,
	String title,
	String reason,
	int amount,
	ChargeStatus status,
	LocalDate dueDate
) {

	public static ChargeItemResult from(ChargeItem chargeItem) {
		return new ChargeItemResult(
			chargeItem.id(),
			chargeItem.campusId(),
			chargeItem.userId(),
			chargeItem.paymentCategory(),
			chargeItem.paymentAccountId(),
			chargeItem.bankNameSnapshot(),
			chargeItem.accountNumberSnapshot(),
			chargeItem.accountHolderSnapshot(),
			chargeItem.sourceType(),
			chargeItem.sourceId(),
			chargeItem.title(),
			chargeItem.reason(),
			chargeItem.amount(),
			chargeItem.status(),
			chargeItem.dueDate()
		);
	}
}
