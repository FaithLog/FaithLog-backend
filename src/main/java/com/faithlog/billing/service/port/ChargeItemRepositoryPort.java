package com.faithlog.billing.service.port;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.service.query.ChargeSearchCriteria;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChargeItemRepositoryPort {

	ChargeItem save(ChargeItem chargeItem);

	Optional<ChargeItem> findChargeItemById(Long chargeItemId);

	Optional<ChargeItem> findChargeItemByIdForUpdate(Long chargeItemId);

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
