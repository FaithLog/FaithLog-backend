package com.faithlog.billing.service.result;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.Instant;
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
	LocalDate dueDate,
	Instant paidAt
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
			chargeItem.dueDate(),
			chargeItem.paidAt()
		);
	}
}
