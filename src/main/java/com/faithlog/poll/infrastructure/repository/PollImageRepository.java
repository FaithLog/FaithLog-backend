package com.faithlog.poll.infrastructure.repository;

import com.faithlog.poll.domain.entity.PollImage;
import java.time.Instant;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollImageRepository extends JpaRepository<PollImage, Long> {

	List<PollImage> findByPollIdOrderByDisplayOrderAscIdAsc(Long pollId);

	List<PollImage> findByPollIdInOrderByPollIdAscDisplayOrderAscIdAsc(List<Long> pollIds);

	void deleteByPollId(Long pollId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PollImage image where image.pollId in :pollIds")
	int deleteByPollIdIn(@Param("pollIds") Collection<Long> pollIds);

	@Query("""
		select image.mediaAssetId from PollImage image
		where image.pollId <> :pollId
			and image.mediaAssetId in :assetIds
		order by image.mediaAssetId
		""")
	List<Long> findAttachedAssetIdsForOtherPolls(
		@Param("pollId") Long pollId,
		@Param("assetIds") List<Long> assetIds
	);

	@Query("select image.mediaAssetId from PollImage image where image.mediaAssetId in :assetIds")
	List<Long> findAttachedAssetIds(@Param("assetIds") List<Long> assetIds);

	@Query("""
		select image.mediaAssetId from PollImage image
		join Poll poll on poll.id = image.pollId
		where poll.campusId = :campusId
			and image.mediaAssetId in :assetIds
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
		select image.mediaAssetId from PollImage image
		join Poll poll on poll.id = image.pollId
		where poll.campusId = :campusId
			and poll.createdBy = :requesterId
			and image.mediaAssetId in :assetIds
		""")
	List<Long> findCreatorAttachedAssetIds(
		@Param("campusId") Long campusId,
		@Param("requesterId") Long requesterId,
		@Param("assetIds") List<Long> assetIds
	);

	@Query("""
		select image.mediaAssetId from PollImage image
		join Poll poll on poll.id = image.pollId
		where poll.campusId = :campusId
			and poll.pollType = com.faithlog.poll.domain.type.PollType.MEAL
			and image.mediaAssetId in :assetIds
		""")
	List<Long> findMealDutyManageableAttachedAssetIds(
		@Param("campusId") Long campusId,
		@Param("assetIds") List<Long> assetIds
	);
}
