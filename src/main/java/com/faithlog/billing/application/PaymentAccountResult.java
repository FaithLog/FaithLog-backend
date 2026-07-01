package com.faithlog.billing.application;

import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import java.time.Instant;

public record PaymentAccountResult(
	Long id,
	Long campusId,
	PaymentCategory accountType,
	String nickname,
	String bankName,
	String accountNumber,
	String accountHolder,
	Long ownerUserId,
	boolean isActive,
	Instant createdAt,
	Instant deactivatedAt
) {

	public static PaymentAccountResult from(PaymentAccount account) {
		return new PaymentAccountResult(
			account.id(),
			account.campusId(),
			account.accountType(),
			account.nickname(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			account.ownerUserId(),
			account.isActive(),
			account.createdAt(),
			account.deactivatedAt()
		);
	}
}
