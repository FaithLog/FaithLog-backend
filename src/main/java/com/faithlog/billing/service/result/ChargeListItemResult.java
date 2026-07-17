package com.faithlog.billing.service.result;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.Instant;
import java.time.LocalDate;

public record ChargeListItemResult(
	Long id,
	PaymentCategory paymentCategory,
	String title,
	String reason,
	int amount,
	ChargeStatus status,
	LocalDate dueDate,
	Instant paidAt,
	ChargeAccountResult account,
	ChargeSourceResult source
) {

	public static ChargeListItemResult from(ChargeItem chargeItem) {
		return new ChargeListItemResult(
			chargeItem.id(),
			chargeItem.paymentCategory(),
			chargeItem.title(),
			chargeItem.reason(),
			chargeItem.amount(),
			chargeItem.status(),
			chargeItem.dueDate(),
			chargeItem.paidAt(),
			new ChargeAccountResult(
				chargeItem.paymentAccountId(),
				chargeItem.bankNameSnapshot(),
				chargeItem.accountNumberSnapshot(),
				chargeItem.accountHolderSnapshot()
			),
			new ChargeSourceResult(chargeItem.sourceType(), chargeItem.sourceId())
		);
	}
}
