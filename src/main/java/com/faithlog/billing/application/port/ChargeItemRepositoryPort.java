package com.faithlog.billing.application.port;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.application.ChargeSearchCriteria;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChargeItemRepositoryPort {

	ChargeItem save(ChargeItem chargeItem);

	Optional<ChargeItem> findChargeItemById(Long chargeItemId);

	Page<ChargeItem> searchCharges(ChargeSearchCriteria criteria, Pageable pageable);

	List<ChargeItem> searchCharges(ChargeSearchCriteria criteria);

	List<ChargeItem> findByCampusIdAndPaymentCategoryAndStatus(
		Long campusId,
		PaymentCategory paymentCategory,
		ChargeStatus status
	);

	Optional<ChargeItem> findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
		Long campusId,
		Long userId,
		PaymentCategory paymentCategory,
		ChargeSourceType sourceType,
		Long sourceId
	);
}
