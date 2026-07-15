package com.faithlog.billing.infrastructure.repository;

import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.ChargeItemLockScope;
import com.faithlog.billing.service.query.ChargeSearchCriteria;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long>, JpaSpecificationExecutor<ChargeItem>, ChargeItemRepositoryPort {

	@Override
	default Optional<ChargeItem> findChargeItemById(Long chargeItemId) {
		return findById(chargeItemId);
	}

	@Query("""
		select charge.campusId as campusId,
			charge.userId as userId,
			charge.paymentCategory as paymentCategory,
			charge.paymentAccountId as paymentAccountId,
			charge.status as status
		from ChargeItem charge
		where charge.id = :chargeItemId
		""")
	Optional<ChargeItemLockScope> findChargeItemLockScopeById(@Param("chargeItemId") Long chargeItemId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select charge from ChargeItem charge where charge.id = :chargeItemId")
	Optional<ChargeItem> findChargeItemByIdForUpdate(@Param("chargeItemId") Long chargeItemId);

	@Override
	default Page<ChargeItem> searchCharges(ChargeSearchCriteria criteria, Pageable pageable) {
		return findAll((root, query, criteriaBuilder) -> {
			List<Predicate> predicates = searchPredicates(criteria, root, criteriaBuilder);
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		}, pageable);
	}

	@Override
	default List<ChargeItem> searchCharges(ChargeSearchCriteria criteria) {
		return findAll((root, query, criteriaBuilder) -> {
			List<Predicate> predicates = searchPredicates(criteria, root, criteriaBuilder);
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		});
	}

	private static List<Predicate> searchPredicates(
		ChargeSearchCriteria criteria,
		jakarta.persistence.criteria.Root<ChargeItem> root,
		jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
	) {
		List<Predicate> predicates = new ArrayList<>();
		predicates.add(criteriaBuilder.equal(root.get("campusId"), criteria.campusId()));
		if (criteria.userIds() != null) {
			if (criteria.userIds().isEmpty()) {
				predicates.add(criteriaBuilder.disjunction());
			} else {
				predicates.add(root.get("userId").in(criteria.userIds()));
			}
		}
		if (criteria.paymentCategory() != null) {
			predicates.add(criteriaBuilder.equal(root.get("paymentCategory"), criteria.paymentCategory()));
		}
		if (criteria.excludedPaymentCategory() != null) {
			predicates.add(criteriaBuilder.notEqual(root.get("paymentCategory"), criteria.excludedPaymentCategory()));
		}
		if (criteria.status() != null) {
			predicates.add(criteriaBuilder.equal(root.get("status"), criteria.status()));
		}
		if (criteria.terminalCompletedAtFrom() != null) {
			Predicate unpaid = criteriaBuilder.equal(root.get("status"), ChargeStatus.UNPAID);
			Predicate recentPaid = criteriaBuilder.and(
				criteriaBuilder.equal(root.get("status"), ChargeStatus.PAID),
				criteriaBuilder.greaterThanOrEqualTo(root.get("paidAt"), criteria.terminalCompletedAtFrom())
			);
			Predicate recentWaivedOrCanceled = criteriaBuilder.and(
				root.get("status").in(ChargeStatus.WAIVED, ChargeStatus.CANCELED),
				criteriaBuilder.greaterThanOrEqualTo(root.get("updatedAt"), criteria.terminalCompletedAtFrom())
			);
			predicates.add(criteriaBuilder.or(unpaid, recentPaid, recentWaivedOrCanceled));
		}
		if (criteria.paymentAccountIds() != null) {
			if (criteria.paymentAccountIds().isEmpty()) {
				predicates.add(criteriaBuilder.disjunction());
			} else {
				predicates.add(root.get("paymentAccountId").in(criteria.paymentAccountIds()));
			}
		}
		return predicates;
	}

	List<ChargeItem> findByCampusIdAndPaymentCategoryAndStatus(
		Long campusId,
		PaymentCategory paymentCategory,
		ChargeStatus status
	);

	List<ChargeItem> findByCampusIdAndPaymentCategoryAndStatusAndPaymentAccountIdInOrderByIdAsc(
		Long campusId,
		PaymentCategory paymentCategory,
		ChargeStatus status,
		Set<Long> paymentAccountIds
	);

	boolean existsByCampusIdAndPaymentCategoryAndStatusAndPaymentAccountIdIn(
		Long campusId,
		PaymentCategory paymentCategory,
		ChargeStatus status,
		Set<Long> paymentAccountIds
	);

	List<ChargeItem> findByCampusIdAndStatus(Long campusId, ChargeStatus status);

	Optional<ChargeItem> findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
		Long campusId,
		Long userId,
		PaymentCategory paymentCategory,
		ChargeSourceType sourceType,
		Long sourceId
	);

	List<ChargeItem> findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInOrderByIdAsc(
		Long campusId,
		PaymentCategory paymentCategory,
		ChargeSourceType sourceType,
		List<Long> sourceIds
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select charge from ChargeItem charge
		where charge.campusId = :campusId
			and charge.userId = :userId
			and charge.paymentCategory = :paymentCategory
			and charge.sourceType = :sourceType
			and charge.sourceId = :sourceId
		""")
	Optional<ChargeItem> findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceIdForUpdate(
		@Param("campusId") Long campusId,
		@Param("userId") Long userId,
		@Param("paymentCategory") PaymentCategory paymentCategory,
		@Param("sourceType") ChargeSourceType sourceType,
		@Param("sourceId") Long sourceId
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		delete from ChargeItem charge
		where charge.status in :statuses
			and charge.createdAt >= :startInclusive
			and charge.createdAt < :endExclusive
		""")
	int deleteByStatusInAndCreatedAtBetween(
		@Param("statuses") List<ChargeStatus> statuses,
		@Param("startInclusive") java.time.Instant startInclusive,
		@Param("endExclusive") java.time.Instant endExclusive
	);
}
