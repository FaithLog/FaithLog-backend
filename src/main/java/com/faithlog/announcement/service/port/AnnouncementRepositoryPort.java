package com.faithlog.announcement.service.port;

import com.faithlog.announcement.domain.entity.Announcement;
import com.faithlog.announcement.domain.type.AnnouncementStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AnnouncementRepositoryPort {

	Announcement save(Announcement announcement);

	Optional<Announcement> findByCampusIdAndId(Long campusId, Long announcementId);

	Optional<Announcement> findByCampusIdAndIdForUpdate(Long campusId, Long announcementId);
	Optional<Announcement> findByIdForUpdate(Long announcementId);

	Page<Announcement> findByCampusIdAndStatus(Long campusId, AnnouncementStatus status, Pageable pageable);

	List<Long> findDueScheduledIds(Instant now, Pageable pageable);
}
