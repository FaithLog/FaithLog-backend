package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollDocument;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollDocumentRepository extends JpaRepository<PollDocument, Long> {
	List<PollDocument> findByPollIdOrderByDisplayOrderAscIdAsc(Long pollId);
	List<PollDocument> findByPollIdInOrderByPollIdAscDisplayOrderAscIdAsc(List<Long> pollIds);
	void deleteByPollId(Long pollId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PollDocument document where document.pollId in :pollIds")
	int deleteByPollIdIn(@Param("pollIds") Collection<Long> pollIds);

	@Query("""
		select document.mediaAssetId from PollDocument document
		where document.pollId <> :pollId and document.mediaAssetId in :assetIds
		order by document.mediaAssetId
		""")
	List<Long> findAttachedAssetIdsForOtherPolls(@Param("pollId") Long pollId, @Param("assetIds") List<Long> assetIds);

	@Query("select document.mediaAssetId from PollDocument document where document.mediaAssetId in :assetIds")
	List<Long> findAttachedAssetIds(@Param("assetIds") List<Long> assetIds);

	@Query("""
		select document.mediaAssetId from PollDocument document
		join Poll poll on poll.id = document.pollId
		where poll.campusId = :campusId
			and document.mediaAssetId in :assetIds
			and (
				(poll.status = com.faithlog.poll.domain.type.PollStatus.OPEN
					and poll.startsAt <= :now and poll.endsAt > :now)
				or (poll.status = com.faithlog.poll.domain.type.PollStatus.CLOSED
					and poll.endsAt >= :closedCutoff)
			)
		""")
	List<Long> findVisibleAttachedAssetIds(
		@Param("campusId") Long campusId,
		@Param("assetIds") List<Long> assetIds,
		@Param("now") Instant now,
		@Param("closedCutoff") Instant closedCutoff
	);

	@Query("""
		select document.mediaAssetId from PollDocument document
		join Poll poll on poll.id = document.pollId
		where poll.campusId = :campusId
			and poll.createdBy = :requesterId
			and document.mediaAssetId in :assetIds
		""")
	List<Long> findCreatorAttachedAssetIds(
		@Param("campusId") Long campusId,
		@Param("requesterId") Long requesterId,
		@Param("assetIds") List<Long> assetIds
	);

	@Query("""
		select document.mediaAssetId from PollDocument document
		join Poll poll on poll.id = document.pollId
		where poll.campusId = :campusId
			and poll.pollType = com.faithlog.poll.domain.type.PollType.MEAL
			and document.mediaAssetId in :assetIds
		""")
	List<Long> findMealDutyManageableAttachedAssetIds(
		@Param("campusId") Long campusId,
		@Param("assetIds") List<Long> assetIds
	);
}
