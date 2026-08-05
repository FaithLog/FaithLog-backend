package com.faithlog.announcement.infrastructure.repository;

import com.faithlog.announcement.domain.entity.AnnouncementDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementDocumentRepository extends JpaRepository<AnnouncementDocument, Long> {
	List<AnnouncementDocument> findByAnnouncementIdOrderByDisplayOrderAscIdAsc(Long announcementId);
	List<AnnouncementDocument> findByAnnouncementIdInOrderByAnnouncementIdAscDisplayOrderAscIdAsc(List<Long> ids);
	void deleteByAnnouncementId(Long announcementId);

	@Query("""
		select document.mediaAssetId from AnnouncementDocument document
		where document.announcementId <> :announcementId and document.mediaAssetId in :assetIds
		order by document.mediaAssetId
		""")
	List<Long> findAttachedAssetIdsForOtherAnnouncements(
		@Param("announcementId") Long announcementId, @Param("assetIds") List<Long> assetIds);

	@Query("select document.mediaAssetId from AnnouncementDocument document where document.mediaAssetId in :assetIds")
	List<Long> findAttachedAssetIds(@Param("assetIds") List<Long> assetIds);

	@Query("""
		select document.mediaAssetId from AnnouncementDocument document
		join Announcement announcement on announcement.id = document.announcementId
		where announcement.campusId = :campusId
			and announcement.status = com.faithlog.announcement.domain.type.AnnouncementStatus.PUBLISHED
			and document.mediaAssetId in :assetIds
		""")
	List<Long> findPublishedAttachedAssetIds(
		@Param("campusId") Long campusId,
		@Param("assetIds") List<Long> assetIds
	);
}
