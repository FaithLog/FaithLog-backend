package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.type.PollType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.type.ChargeGenerationType;

public interface PollTemplateRepository extends JpaRepository<PollTemplate, Long> {

	@Query("""
		select template.campusId as campusId,
			template.pollType as pollType,
			template.chargeGenerationType as chargeGenerationType,
			template.paymentCategory as paymentCategory
		from PollTemplate template
		where template.id = :templateId
		""")
	Optional<PollTemplateLockScope> findLockScopeById(@Param("templateId") Long templateId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select template from PollTemplate template where template.id = :templateId")
	Optional<PollTemplate> findByIdForUpdate(@Param("templateId") Long templateId);

	Optional<PollTemplate> findByCampusIdAndPollTypeAndIsDefaultTrue(Long campusId, PollType pollType);

	List<PollTemplate> findByCampusIdAndIsActiveTrueOrderByIdAsc(Long campusId);

	List<PollTemplate> findByIsActiveTrueAndAutoCreateEnabledTrueOrderByIdAsc();

	interface PollTemplateLockScope {

		Long getCampusId();

		PollType getPollType();

		ChargeGenerationType getChargeGenerationType();

		PaymentCategory getPaymentCategory();
	}
}
