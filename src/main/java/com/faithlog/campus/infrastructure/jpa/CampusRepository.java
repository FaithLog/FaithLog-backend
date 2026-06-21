package com.faithlog.campus.infrastructure.jpa;

import com.faithlog.admin.application.AdminCampusSearchCriteria;
import com.faithlog.admin.application.AdminCampusStatus;
import com.faithlog.admin.application.port.AdminCampusRepositoryPort;
import com.faithlog.campus.application.port.CampusRepositoryPort;
import com.faithlog.campus.domain.Campus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusRepository extends JpaRepository<Campus, Long>, JpaSpecificationExecutor<Campus>, CampusRepositoryPort, AdminCampusRepositoryPort {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select campus from Campus campus where campus.id = :campusId")
	Optional<Campus> findByIdForUpdate(@Param("campusId") Long campusId);

	Optional<Campus> findByInviteCode(String inviteCode);

	boolean existsByInviteCode(String inviteCode);

	List<Campus> findByIsActiveTrueOrderByIdAsc();

	@Override
	default Page<Campus> searchAdminCampuses(AdminCampusSearchCriteria criteria, Pageable pageable) {
		return findAll((root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.name() != null && !criteria.name().isBlank()) {
				predicates.add(criteriaBuilder.like(
					criteriaBuilder.lower(root.get("name")),
					"%" + criteria.name().toLowerCase() + "%"
				));
			}
			if (criteria.region() != null && !criteria.region().isBlank()) {
				predicates.add(criteriaBuilder.like(
					criteriaBuilder.lower(root.get("region")),
					"%" + criteria.region().toLowerCase() + "%"
				));
			}
			if (criteria.status() != null) {
				predicates.add(criteriaBuilder.equal(root.get("isActive"), criteria.status() == AdminCampusStatus.ACTIVE));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		}, pageable);
	}
}
