package com.faithlog.announcement.infrastructure.repository;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.port.AnnouncementRepositoryPort;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long>, AnnouncementRepositoryPort {

	Optional<Announcement> findByCampusIdAndId(Long campusId, Long announcementId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select announcement
		from Announcement announcement
		where announcement.campusId = :campusId and announcement.id = :announcementId
		""")
	Optional<Announcement> findByCampusIdAndIdForUpdate(
		@Param("campusId") Long campusId,
		@Param("announcementId") Long announcementId
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select announcement from Announcement announcement where announcement.id = :announcementId")
	Optional<Announcement> findByIdForUpdate(@Param("announcementId") Long announcementId);

	Page<Announcement> findByCampusIdAndStatus(
		Long campusId,
		AnnouncementStatus status,
		Pageable pageable
	);

	@Query("""
		select announcement.id
		from Announcement announcement
		where announcement.status = com.faithlog.announcement.domain.type.AnnouncementStatus.SCHEDULED
			and announcement.publishAt <= :now
		order by announcement.publishAt asc, announcement.id asc
		""")
	List<Long> findDueScheduledIds(@Param("now") Instant now, Pageable pageable);
}
