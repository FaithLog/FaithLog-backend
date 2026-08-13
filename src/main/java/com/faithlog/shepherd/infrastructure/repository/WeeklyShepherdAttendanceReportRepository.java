package com.faithlog.shepherd.infrastructure.repository;

import com.faithlog.shepherd.domain.entity.WeeklyShepherdAttendanceReport;
import com.faithlog.shepherd.service.result.ShepherdAttendanceSummaryRow;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface WeeklyShepherdAttendanceReportRepository extends JpaRepository<WeeklyShepherdAttendanceReport, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select report
		from WeeklyShepherdAttendanceReport report
		where report.campusId = :campusId
			and report.shepherdGroupId = :groupId
			and report.serviceDate = :serviceDate
		""")
	Optional<WeeklyShepherdAttendanceReport> findBySlotForUpdate(
		@Param("campusId") Long campusId,
		@Param("groupId") Long groupId,
		@Param("serviceDate") LocalDate serviceDate
	);

	Optional<WeeklyShepherdAttendanceReport> findByCampusIdAndShepherdGroupIdAndServiceDate(
		Long campusId,
		Long shepherdGroupId,
		LocalDate serviceDate
	);

	@Query("""
		select new com.faithlog.shepherd.service.result.ShepherdAttendanceSummaryRow(
			count(shepherdGroup.id),
			coalesce(sum(case when report.status = com.faithlog.shepherd.domain.type.WeeklyShepherdAttendanceStatus.SUBMITTED then 1 else 0 end), 0),
			coalesce(sum(report.smallGroupMeetingCount), 0),
			coalesce(sum(report.holyWaveCount), 0),
			coalesce(sum(report.otherWorshipCount), 0)
		)
		from ShepherdGroup shepherdGroup
		left join WeeklyShepherdAttendanceReport report
			on report.shepherdGroupId = shepherdGroup.id
			and report.serviceDate = :serviceDate
		where shepherdGroup.campusId = :campusId
			and shepherdGroup.status = com.faithlog.shepherd.domain.type.ShepherdGroupStatus.ACTIVE
		""")
	ShepherdAttendanceSummaryRow summarizeCampusServiceDate(
		@Param("campusId") Long campusId,
		@Param("serviceDate") LocalDate serviceDate
	);
}
