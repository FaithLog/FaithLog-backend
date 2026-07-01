package com.faithlog.billing.application.port;

import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;
import java.util.Optional;

public interface PaymentAccountRepositoryPort {

	PaymentAccount save(PaymentAccount paymentAccount);

	Optional<PaymentAccount> findById(Long accountId);

	Optional<PaymentAccount> findByCampusIdAndAccountTypeAndIsActiveTrue(Long campusId, PaymentCategory accountType);

	Optional<PaymentAccount> findByCampusIdAndAccountTypeAndOwnerUserIdAndIsActiveTrue(
		Long campusId,
		PaymentCategory accountType,
		Long ownerUserId
	);

	List<PaymentAccount> findByCampusIdAndIsActiveTrueOrderByIdAsc(Long campusId);

	List<PaymentAccount> findByCampusIdOrderByIdAsc(Long campusId);

	List<PaymentAccount> findByCampusIdAndOwnerUserIdAndIsActiveTrueOrderByIdAsc(Long campusId, Long ownerUserId);

	List<PaymentAccount> findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueOrderByIdAsc(
		Long campusId,
		Long ownerUserId,
		PaymentCategory accountType
	);
}
