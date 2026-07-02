package com.faithlog.billing.application.port;

import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;
import java.util.Optional;

public interface PaymentAccountRepositoryPort {

	PaymentAccount save(PaymentAccount paymentAccount);

	void flush();

	Optional<PaymentAccount> findById(Long accountId);

	Optional<PaymentAccount> findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(Long campusId, PaymentCategory accountType);

	Optional<PaymentAccount> findByCampusIdAndAccountTypeAndOwnerUserIdAndIsActiveTrueAndDeletedAtIsNull(
		Long campusId,
		PaymentCategory accountType,
		Long ownerUserId
	);

	List<PaymentAccount> findByCampusIdAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(Long campusId);

	List<PaymentAccount> findByCampusIdAndDeletedAtIsNullOrderByIdAsc(Long campusId);

	List<PaymentAccount> findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
		Long campusId,
		PaymentCategory accountType
	);

	List<PaymentAccount> findByCampusIdAndAccountTypeAndDeletedAtIsNullOrderByIdAsc(
		Long campusId,
		PaymentCategory accountType
	);

	List<PaymentAccount> findByCampusIdAndOwnerUserIdAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(Long campusId, Long ownerUserId);

	List<PaymentAccount> findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNullOrderByIdAsc(
		Long campusId,
		Long ownerUserId,
		PaymentCategory accountType
	);
}
