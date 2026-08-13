package com.faithlog.shepherd.infrastructure.repository;

import com.faithlog.shepherd.service.result.ShepherdRequesterAccessRow;
import com.faithlog.user.domain.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ShepherdAccessRepository extends Repository<User, Long> {

	@Query("""
		select new com.faithlog.shepherd.service.result.ShepherdRequesterAccessRow(
			user.id,
			user.name,
			user.email,
			user.role,
			user.isActive,
			member.id,
			member.campusRole,
			member.status
		)
		from User user
		left join CampusMember member
			on member.userId = user.id
			and member.campusId = :campusId
		where user.id = :userId
		""")
	Optional<ShepherdRequesterAccessRow> findRequesterAccess(
		@Param("campusId") Long campusId,
		@Param("userId") Long userId
	);
}
