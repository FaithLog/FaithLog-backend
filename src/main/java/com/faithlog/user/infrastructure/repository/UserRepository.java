package com.faithlog.user.infrastructure.repository;

import com.faithlog.admin.service.query.AdminUserSearchCriteria;
import com.faithlog.admin.service.port.AdminUserRepositoryPort;
import com.faithlog.campus.service.port.CampusUserLookupPort;
import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.campus.service.port.CampusUserTokenVersionPort;
import com.faithlog.user.domain.entity.User;
import jakarta.persistence.LockModeType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User>, CampusUserLookupPort, AdminUserRepositoryPort, CampusUserTokenVersionPort {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user where user.id = :userId")
	Optional<User> findByIdForUpdate(@Param("userId") Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user where user.email = :email")
	Optional<User> findByEmailForUpdate(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select user from User user where user.id in :userIds order by user.id asc")
	List<User> findUsersByIdsForUpdate(@Param("userIds") Collection<Long> userIds);

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
	default Optional<CampusUserLookupResult> findCampusUserByIdForUpdate(Long userId) {
		return findByIdForUpdate(userId).map(UserRepository::campusUser);
	}

	@Override
	default List<CampusUserLookupResult> findCampusUsersByIds(Collection<Long> userIds) {
		return findAllById(userIds).stream()
			.map(UserRepository::campusUser)
			.toList();
	}

	@Override
	default List<CampusUserLookupResult> findCampusUsersByIdsForUpdate(Collection<Long> userIds) {
		return findUsersByIdsForUpdate(userIds).stream()
			.map(UserRepository::campusUser)
			.toList();
	}

	@Override
	default List<User> findAdminUsersByIdsForUpdate(Collection<Long> userIds) {
		return findUsersByIdsForUpdate(userIds);
	}

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	long countByRoleAndIsActiveTrue(com.faithlog.user.domain.type.UserRole role);

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

	private static CampusUserLookupResult campusUser(User user) {
		return new CampusUserLookupResult(
			user.id(),
			user.name(),
			user.email(),
			user.role().name(),
			user.isActive()
		);
	}
}
