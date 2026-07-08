package com.faithlog.prayer.infrastructure.jpa;

import com.faithlog.prayer.domain.PrayerSubmission;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrayerSubmissionRepository extends JpaRepository<PrayerSubmission, Long> {

	List<PrayerSubmission> findByPrayerWeekId(Long prayerWeekId);

	Optional<PrayerSubmission> findByPrayerWeekIdAndUserId(Long prayerWeekId, Long userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update PrayerSubmission submission
		set submission.content = :content,
			submission.submittedBy = :submittedBy,
			submission.submittedAt = :submittedAt,
			submission.updatedAt = :submittedAt,
			submission.version = submission.version + 1
		where submission.id = :id
			and submission.version = :expectedVersion
		""")
	int updateContentIfVersionMatches(
		@Param("id") Long id,
		@Param("content") String content,
		@Param("submittedBy") Long submittedBy,
		@Param("submittedAt") Instant submittedAt,
		@Param("expectedVersion") int expectedVersion
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update PrayerSubmission submission
		set submission.content = :content,
			submission.submittedBy = :submittedBy,
			submission.submittedAt = :submittedAt,
			submission.updatedAt = :submittedAt,
			submission.version = submission.version + 1
		where submission.id = :id
		""")
	int updateContent(
		@Param("id") Long id,
		@Param("content") String content,
		@Param("submittedBy") Long submittedBy,
		@Param("submittedAt") Instant submittedAt
	);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from PrayerSubmission submission where submission.createdAt < :createdAt")
	int deleteByCreatedAtBefore(@Param("createdAt") Instant createdAt);
}
