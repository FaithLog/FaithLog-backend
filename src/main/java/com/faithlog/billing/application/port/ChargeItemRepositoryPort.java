package com.faithlog.billing.application.port;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;

public interface ChargeItemRepositoryPort {

	ChargeItem save(ChargeItem chargeItem);

	List<ChargeItem> findByCampusIdAndPaymentCategoryAndStatus(
		Long campusId,
		PaymentCategory paymentCategory,
		ChargeStatus status
	);
}
