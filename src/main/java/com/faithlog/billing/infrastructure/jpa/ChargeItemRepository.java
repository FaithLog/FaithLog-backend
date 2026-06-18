package com.faithlog.billing.infrastructure.jpa;

import com.faithlog.billing.application.port.ChargeItemRepositoryPort;
import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long>, ChargeItemRepositoryPort {

	@Override
	default Optional<ChargeItem> findChargeItemById(Long chargeItemId) {
		return findById(chargeItemId);
	}

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
