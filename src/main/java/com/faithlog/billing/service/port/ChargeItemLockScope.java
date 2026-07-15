package com.faithlog.billing.service.port;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;

public interface ChargeItemLockScope {

	Long getCampusId();

	Long getUserId();

	PaymentCategory getPaymentCategory();

	Long getPaymentAccountId();

	ChargeStatus getStatus();
}
