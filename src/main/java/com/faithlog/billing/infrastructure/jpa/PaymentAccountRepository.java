package com.faithlog.billing.infrastructure.jpa;

import com.faithlog.billing.application.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, Long>, PaymentAccountRepositoryPort {

	Optional<PaymentAccount> findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(Long campusId, PaymentCategory accountType);

	Optional<PaymentAccount> findByCampusIdAndAccountTypeAndOwnerUserIdAndIsActiveTrueAndDeletedAtIsNull(
		Long campusId,
		PaymentCategory accountType,
		Long ownerUserId
	);

	List<PaymentAccount> findByCampusIdAndAccountTypeOrderByIdAsc(Long campusId, PaymentCategory accountType);

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
