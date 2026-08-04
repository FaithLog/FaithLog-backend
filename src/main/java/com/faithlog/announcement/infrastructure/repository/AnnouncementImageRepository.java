package com.faithlog.announcement.infrastructure.repository;

import com.faithlog.announcement.domain.entity.AnnouncementImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementImageRepository extends JpaRepository<AnnouncementImage, Long> {
	List<AnnouncementImage> findByAnnouncementIdOrderByDisplayOrderAscIdAsc(Long announcementId);
	List<AnnouncementImage> findByAnnouncementIdInOrderByAnnouncementIdAscDisplayOrderAscIdAsc(
		List<Long> announcementIds);
	void deleteByAnnouncementId(Long announcementId);

	@Query("""
		select image.mediaAssetId from AnnouncementImage image
		where image.announcementId <> :announcementId
			and image.mediaAssetId in :assetIds
		order by image.mediaAssetId
		""")
	List<Long> findAttachedAssetIdsForOtherAnnouncements(
		@Param("announcementId") Long announcementId,
		@Param("assetIds") List<Long> assetIds
	);

	@Query("select image.mediaAssetId from AnnouncementImage image where image.mediaAssetId in :assetIds")
	List<Long> findAttachedAssetIds(@Param("assetIds") List<Long> assetIds);

	@Query("""
		select image.mediaAssetId from AnnouncementImage image
		join Announcement announcement on announcement.id = image.announcementId
		where announcement.campusId = :campusId
			and announcement.status = com.faithlog.announcement.domain.type.AnnouncementStatus.PUBLISHED
			and image.mediaAssetId in :assetIds
		""")
	List<Long> findPublishedAttachedAssetIds(@Param("campusId") Long campusId, @Param("assetIds") List<Long> assetIds);
}
