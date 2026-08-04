package com.faithlog.announcement.infrastructure.repository;

import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementCategoryRepository
	extends JpaRepository<AnnouncementCategory, Long>, AnnouncementCategoryRepositoryPort {

	@Query("""
		select case when count(category) > 0 then true else false end
		from AnnouncementCategory category
		where category.campusId = :campusId
			and lower(category.name) = lower(:name)
		""")
	boolean existsByCampusIdAndNameIgnoreCase(@Param("campusId") Long campusId, @Param("name") String name);

	@Query("""
		select case when count(category) > 0 then true else false end
		from AnnouncementCategory category
		where category.campusId = :campusId
			and lower(category.name) = lower(:name)
			and category.id <> :categoryId
		""")
	boolean existsByCampusIdAndNameIgnoreCaseAndIdNot(
		@Param("campusId") Long campusId,
		@Param("name") String name,
		@Param("categoryId") Long categoryId
	);

	Optional<AnnouncementCategory> findByCampusIdAndId(Long campusId, Long categoryId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select category
		from AnnouncementCategory category
		where category.campusId = :campusId and category.id = :categoryId
		""")
	Optional<AnnouncementCategory> findByCampusIdAndIdForUpdate(
		@Param("campusId") Long campusId,
		@Param("categoryId") Long categoryId
	);

	@Query("""
		select category
		from AnnouncementCategory category
		where category.campusId = :campusId and category.id in :categoryIds
		order by category.id asc
		""")
	List<AnnouncementCategory> findByCampusIdAndIdIn(
		@Param("campusId") Long campusId,
		@Param("categoryIds") List<Long> categoryIds
	);

	List<AnnouncementCategory> findByCampusIdOrderByDisplayOrderAscNameAscIdAsc(Long campusId);
}
