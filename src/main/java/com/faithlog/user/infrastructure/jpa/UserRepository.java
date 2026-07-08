package com.faithlog.user.infrastructure.jpa;

import com.faithlog.admin.application.AdminUserSearchCriteria;
import com.faithlog.admin.application.port.AdminUserRepositoryPort;
import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.campus.application.port.CampusUserTokenVersionPort;
import com.faithlog.user.domain.User;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>, CampusUserLookupPort, AdminUserRepositoryPort, CampusUserTokenVersionPort {

	@Override
	default Optional<CampusUserLookupResult> findCampusUserById(Long userId) {
		return findById(userId)
			.map(user -> new CampusUserLookupResult(
				user.id(),
				user.name(),
				user.email(),
				user.role().name(),
				user.isActive()
			));
	}

	@Override
	default List<CampusUserLookupResult> findCampusUsersByIds(Collection<Long> userIds) {
		return findAllById(userIds).stream()
			.map(user -> new CampusUserLookupResult(
				user.id(),
				user.name(),
				user.email(),
				user.role().name(),
				user.isActive()
			))
			.toList();
	}

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	long countByRoleAndIsActiveTrue(com.faithlog.user.domain.UserRole role);

	@Override
	default void increaseTokenVersion(Long userId) {
		findById(userId).ifPresent(User::increaseTokenVersion);
	}

	@Override
	default Optional<User> findAdminUserById(Long userId) {
		return findById(userId);
	}

	@Override
	default Page<User> searchAdminUsers(AdminUserSearchCriteria criteria, Pageable pageable) {
		return findAll((root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (criteria.name() != null && !criteria.name().isBlank()) {
				predicates.add(criteriaBuilder.like(
					criteriaBuilder.lower(root.get("name")),
					"%" + criteria.name().toLowerCase() + "%"
				));
			}
			if (criteria.email() != null && !criteria.email().isBlank()) {
				predicates.add(criteriaBuilder.like(
					criteriaBuilder.lower(root.get("email")),
					"%" + criteria.email().toLowerCase() + "%"
				));
			}
			if (criteria.userId() != null) {
				predicates.add(criteriaBuilder.equal(root.get("id"), criteria.userId()));
			}
			if (criteria.role() != null) {
				predicates.add(criteriaBuilder.equal(root.get("role"), criteria.role()));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		}, pageable);
	}
}
