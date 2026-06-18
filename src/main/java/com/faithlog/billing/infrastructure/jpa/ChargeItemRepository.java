package com.faithlog.billing.infrastructure.jpa;

import com.faithlog.billing.application.port.ChargeItemRepositoryPort;
import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long>, ChargeItemRepositoryPort {

	List<ChargeItem> findByCampusIdAndPaymentCategoryAndStatus(
		Long campusId,
		PaymentCategory paymentCategory,
		ChargeStatus status
	);
}
