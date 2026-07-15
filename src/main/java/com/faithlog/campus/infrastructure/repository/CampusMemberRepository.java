package com.faithlog.campus.infrastructure.repository;

import com.faithlog.admin.service.port.AdminCampusMemberRepositoryPort;
import com.faithlog.campus.service.result.CampusMembershipRow;
import com.faithlog.campus.service.port.CampusMemberLockScope;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusMemberRepository extends JpaRepository<CampusMember, Long>, CampusMemberRepositoryPort, AdminCampusMemberRepositoryPort {

	boolean existsByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndId(Long campusId, Long id);

	@Query("""
		select new com.faithlog.campus.service.port.CampusMemberLockScope(
			member.id,
			member.campusId,
			member.userId
		)
		from CampusMember member
		where member.campusId = :campusId
			and member.id = :membershipId
		""")
	Optional<CampusMemberLockScope> findLockScopeByCampusIdAndId(
		@Param("campusId") Long campusId,
		@Param("membershipId") Long membershipId
	);

	@Query("""
		select new com.faithlog.campus.service.port.CampusMemberLockScope(
			member.id,
			member.campusId,
			member.userId
		)
		from CampusMember member
		where member.userId = :userId
		order by member.campusId asc, member.id asc
		""")
	List<CampusMemberLockScope> findLockScopesByUserIdOrderByCampusIdAsc(@Param("userId") Long userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select member
		from CampusMember member
		where member.campusId = :campusId
			and member.id = :membershipId
		""")
	Optional<CampusMember> findByCampusIdAndIdForUpdate(
		@Param("campusId") Long campusId,
		@Param("membershipId") Long membershipId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select member
		from CampusMember member
		where member.campusId = :campusId
			and member.userId = :userId
		""")
	Optional<CampusMember> findByCampusIdAndUserIdForUpdate(
		@Param("campusId") Long campusId,
		@Param("userId") Long userId
	);

	List<CampusMember> findByCampusIdAndStatusOrderByIdAsc(Long campusId, CampusMemberStatus status);

	List<CampusMember> findByCampusIdOrderByIdAsc(Long campusId);

	List<CampusMember> findByUserIdAndStatusOrderByIdDesc(Long userId, CampusMemberStatus status);

	@Query("""
		select new com.faithlog.campus.service.result.CampusMembershipRow(
			member.id,
			campus.id,
			campus.name,
			campus.region,
			member.campusRole,
			member.status
		)
		from CampusMember member
		join Campus campus on campus.id = member.campusId
		where member.userId = :userId
			and member.status = :status
		order by member.id desc
		""")
	List<CampusMembershipRow> findMembershipRowsByUserIdAndStatusOrderByIdDesc(
		@Param("userId") Long userId,
		@Param("status") CampusMemberStatus status
	);

	List<CampusMember> findByUserIdOrderByIdAsc(Long userId);
}
