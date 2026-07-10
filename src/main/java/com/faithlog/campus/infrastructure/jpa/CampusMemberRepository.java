package com.faithlog.campus.infrastructure.jpa;

import com.faithlog.admin.service.port.AdminCampusMemberRepositoryPort;
import com.faithlog.campus.application.CampusMembershipRow;
import com.faithlog.campus.application.port.CampusMemberRepositoryPort;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CampusMemberRepository extends JpaRepository<CampusMember, Long>, CampusMemberRepositoryPort, AdminCampusMemberRepositoryPort {

	boolean existsByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndUserId(Long campusId, Long userId);

	Optional<CampusMember> findByCampusIdAndId(Long campusId, Long id);

	List<CampusMember> findByCampusIdAndStatusOrderByIdAsc(Long campusId, CampusMemberStatus status);

	List<CampusMember> findByCampusIdOrderByIdAsc(Long campusId);

	List<CampusMember> findByUserIdAndStatusOrderByIdDesc(Long userId, CampusMemberStatus status);

	@Query("""
		select new com.faithlog.campus.application.CampusMembershipRow(
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
