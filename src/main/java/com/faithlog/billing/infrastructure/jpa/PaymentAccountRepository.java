package com.faithlog.billing.infrastructure.jpa;

import com.faithlog.billing.application.port.PaymentAccountRepositoryPort;
import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, Long>, PaymentAccountRepositoryPort {

	Optional<PaymentAccount> findByCampusIdAndAccountTypeAndIsActiveTrue(Long campusId, PaymentCategory accountType);

	List<PaymentAccount> findByCampusIdAndAccountTypeOrderByIdAsc(Long campusId, PaymentCategory accountType);

	List<PaymentAccount> findByCampusIdAndIsActiveTrueOrderByIdAsc(Long campusId);

	List<PaymentAccount> findByCampusIdOrderByIdAsc(Long campusId);

	List<PaymentAccount> findByCampusIdAndOwnerUserIdAndIsActiveTrueOrderByIdAsc(Long campusId, Long ownerUserId);

	List<PaymentAccount> findByCampusIdAndOwnerUserIdAndAccountTypeAndIsActiveTrueOrderByIdAsc(
		Long campusId,
		Long ownerUserId,
		PaymentCategory accountType
	);
}
