package com.faithlog.billing.infrastructure.repository;

import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
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

	List<PaymentAccount> findByCampusIdAndOwnerUserIdAndAccountTypeAndDeletedAtIsNullOrderByIdAsc(
		Long campusId,
		Long ownerUserId,
		PaymentCategory accountType
	);

	List<PaymentAccount> findByCampusIdAndOwnerUserIdAndAccountTypeOrderByIdAsc(
		Long campusId,
		Long ownerUserId,
		PaymentCategory accountType
	);
}
