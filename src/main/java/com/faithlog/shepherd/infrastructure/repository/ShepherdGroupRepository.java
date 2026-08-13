package com.faithlog.shepherd.infrastructure.repository;

import com.faithlog.shepherd.domain.entity.ShepherdGroup;
import com.faithlog.shepherd.domain.type.ShepherdGroupStatus;
import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardGroupRow;
import com.faithlog.shepherd.service.result.ShepherdGroupRow;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShepherdGroupRepository extends JpaRepository<ShepherdGroup, Long> {

	boolean existsByCampusIdAndNormalizedName(Long campusId, String normalizedName);

	Optional<ShepherdGroup> findByCampusIdAndIdAndStatus(Long campusId, Long id, ShepherdGroupStatus status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select shepherdGroup
		from ShepherdGroup shepherdGroup
		where shepherdGroup.campusId = :campusId
			and shepherdGroup.id = :groupId
			and shepherdGroup.status = com.faithlog.shepherd.domain.type.ShepherdGroupStatus.ACTIVE
		""")
	Optional<ShepherdGroup> findActiveByCampusIdAndIdForUpdate(
		@Param("campusId") Long campusId,
		@Param("groupId") Long groupId
	);

	@Query("""
		select new com.faithlog.shepherd.service.result.ShepherdGroupRow(
			shepherdGroup.id,
			shepherdGroup.campusId,
			shepherdGroup.name,
			cast(shepherdGroup.status as string),
			shepherdGroup.version
		)
		from ShepherdGroup shepherdGroup
		where shepherdGroup.campusId = :campusId
			and shepherdGroup.status = com.faithlog.shepherd.domain.type.ShepherdGroupStatus.ACTIVE
		order by shepherdGroup.normalizedName asc, shepherdGroup.id asc
		""")
	List<ShepherdGroupRow> findAdminGroupRows(@Param("campusId") Long campusId);

	@Query("""
		select new com.faithlog.shepherd.service.result.ShepherdGroupRow(
			shepherdGroup.id,
			shepherdGroup.campusId,
			shepherdGroup.name,
			cast(shepherdGroup.status as string),
			shepherdGroup.version
		)
		from ShepherdGroupAssignee assignee
		join ShepherdGroup shepherdGroup on shepherdGroup.id = assignee.shepherdGroupId
		where assignee.campusId = :campusId
			and assignee.userId = :userId
			and shepherdGroup.status = com.faithlog.shepherd.domain.type.ShepherdGroupStatus.ACTIVE
		order by shepherdGroup.normalizedName asc, shepherdGroup.id asc
		""")
	List<ShepherdGroupRow> findMyGroupRows(
		@Param("campusId") Long campusId,
		@Param("userId") Long userId
	);

	@Query("""
			select new com.faithlog.shepherd.service.result.ShepherdAttendanceBoardGroupRow(
				shepherdGroup.id,
				shepherdGroup.name,
				shepherdGroup.version,
				report.id,
				report.serviceDate,
				report.smallGroupMeetingCount,
				report.holyWaveCount,
				report.otherWorshipCount,
				report.note,
				cast(report.status as string),
				report.lastModifiedBy,
				modifier.name,
				report.lastModifiedAt,
				report.version
			)
			from ShepherdGroup shepherdGroup
			left join WeeklyShepherdAttendanceReport report
				on report.shepherdGroupId = shepherdGroup.id
				and report.serviceDate = :serviceDate
			left join User modifier on modifier.id = report.lastModifiedBy
			where shepherdGroup.campusId = :campusId
				and shepherdGroup.status = com.faithlog.shepherd.domain.type.ShepherdGroupStatus.ACTIVE
			order by shepherdGroup.normalizedName asc, shepherdGroup.id asc
			"""
	)
	List<ShepherdAttendanceBoardGroupRow> findAdminAttendanceBoardRows(
		@Param("campusId") Long campusId,
		@Param("serviceDate") LocalDate serviceDate,
		Pageable pageable
	);
}
