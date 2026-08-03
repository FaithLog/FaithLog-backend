package com.faithlog.announcement.service.port;

import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import java.util.List;
import java.util.Optional;

public interface AnnouncementCategoryRepositoryPort {

	AnnouncementCategory save(AnnouncementCategory category);

	boolean existsByCampusIdAndNameIgnoreCase(Long campusId, String name);

	boolean existsByCampusIdAndNameIgnoreCaseAndIdNot(Long campusId, String name, Long categoryId);

	Optional<AnnouncementCategory> findByCampusIdAndId(Long campusId, Long categoryId);

	Optional<AnnouncementCategory> findByCampusIdAndIdForUpdate(Long campusId, Long categoryId);

	List<AnnouncementCategory> findByCampusIdAndIdIn(Long campusId, List<Long> categoryIds);

	List<AnnouncementCategory> findByCampusIdOrderByDisplayOrderAscNameAscIdAsc(Long campusId);
}
