package com.faithlog.billing.infrastructure.jpa;

import com.faithlog.billing.application.port.ChargeItemRepositoryPort;
import com.faithlog.billing.application.ChargeSearchCriteria;
import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ChargeItemRepository extends JpaRepository<ChargeItem, Long>, JpaSpecificationExecutor<ChargeItem>, ChargeItemRepositoryPort {

	@Override
	default Optional<ChargeItem> findChargeItemById(Long chargeItemId) {
		return findById(chargeItemId);
	}

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
		if (criteria.status() != null) {
			predicates.add(criteriaBuilder.equal(root.get("status"), criteria.status()));
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

	List<ChargeItem> findByCampusIdAndStatus(Long campusId, ChargeStatus status);

	Optional<ChargeItem> findByCampusIdAndUserIdAndPaymentCategoryAndSourceTypeAndSourceId(
		Long campusId,
		Long userId,
		PaymentCategory paymentCategory,
		ChargeSourceType sourceType,
		Long sourceId
	);
}
