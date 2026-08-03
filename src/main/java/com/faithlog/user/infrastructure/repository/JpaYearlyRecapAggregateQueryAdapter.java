package com.faithlog.user.infrastructure.repository;

import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.user.service.port.CampusRecapActivity;
import com.faithlog.user.service.port.CommentActivityRecapAggregate;
import com.faithlog.user.service.port.DevotionDailyActivity;
import com.faithlog.user.service.port.DevotionRecapSource;
import com.faithlog.user.service.port.PenaltySummaryRecapAggregate;
import com.faithlog.user.service.port.PrayerRecapAggregate;
import com.faithlog.user.service.port.YearlyRecapAggregateQueryPort;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class JpaYearlyRecapAggregateQueryAdapter implements YearlyRecapAggregateQueryPort {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final EntityManager entityManager;

	public JpaYearlyRecapAggregateQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public List<CampusRecapActivity> findActiveCampuses(Long userId, LocalDate endDateExclusive) {
		Instant joinedBefore = endDateExclusive.atStartOfDay(SEOUL).toInstant();
		return entityManager.createQuery("""
			select member.campusId, campus.name, member.joinedAt
			from CampusMember member, Campus campus
			where member.campusId = campus.id
			  and member.userId = :userId
			  and member.status = :status
			  and member.joinedAt < :joinedBefore
			order by member.campusId asc
			""", Object[].class)
			.setParameter("userId", userId)
			.setParameter("status", CampusMemberStatus.ACTIVE)
			.setParameter("joinedBefore", joinedBefore)
			.getResultList()
			.stream()
			.map(row -> new CampusRecapActivity(
				(Long) row[0],
				(String) row[1],
				((Instant) row[2]).atZone(SEOUL).toLocalDate()
			))
			.toList();
	}

	@Override
	public DevotionRecapSource findDevotion(Long userId, LocalDate startDate, LocalDate endDateExclusive) {
		List<DevotionDailyActivity> dailyActivities = entityManager.createQuery("""
			select daily.recordDate, daily.quietTimeChecked, daily.bibleReadingChecked, daily.prayerChecked
			from DevotionDailyCheck daily, WeeklyDevotionRecord weekly
			where daily.weeklyRecordId = weekly.id
			  and weekly.userId = :userId
			  and daily.recordDate >= :startDate
			  and daily.recordDate < :endDateExclusive
			order by daily.recordDate asc, weekly.id asc
			""", Object[].class)
			.setParameter("userId", userId)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.getResultList()
			.stream()
			.map(row -> new DevotionDailyActivity(
				(LocalDate) row[0],
				(Boolean) row[1],
				(Boolean) row[2],
				(Boolean) row[3]
			))
			.toList();
		long submittedWeeks = entityManager.createQuery("""
			select count(distinct weekly.weekStartDate)
			from WeeklyDevotionRecord weekly
			where weekly.userId = :userId
			  and weekly.submittedAt is not null
			  and weekly.weekStartDate >= :startDate
			  and weekly.weekStartDate < :endDateExclusive
			""", Long.class)
			.setParameter("userId", userId)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.getSingleResult();
		return new DevotionRecapSource(dailyActivities, Math.toIntExact(submittedWeeks));
	}

	@Override
	public PrayerRecapAggregate findPrayer(Long userId, LocalDate startDate, LocalDate endDateExclusive) {
		Object[] row = entityManager.createQuery("""
			select count(distinct week.weekStartDate), count(distinct week.seasonId)
			from PrayerSubmission submission, PrayerWeek week
			where submission.prayerWeekId = week.id
			  and submission.userId = :userId
			  and submission.submittedAt is not null
			  and week.weekStartDate >= :startDate
			  and week.weekStartDate < :endDateExclusive
			""", Object[].class)
			.setParameter("userId", userId)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.getSingleResult();
		return new PrayerRecapAggregate(
			Math.toIntExact(((Number) row[0]).longValue()),
			Math.toIntExact(((Number) row[1]).longValue())
		);
	}

	@Override
	public CommentActivityRecapAggregate findCommentActivity(
		Long userId,
		LocalDate startDate,
		LocalDate endDateExclusive
	) {
		Instant startInclusive = startDate.atStartOfDay(SEOUL).toInstant();
		Instant endExclusive = endDateExclusive.atStartOfDay(SEOUL).toInstant();
		long commentCount = entityManager.createQuery("""
			select count(comment.id)
			from PollComment comment, Poll poll
			where comment.pollId = poll.id
			  and comment.userId = :userId
			  and comment.deletedAt is null
			  and exists (
			    select member.id from CampusMember member
			    where member.userId = :userId
			      and member.campusId = poll.campusId
			      and member.status = :activeStatus
			  )
			  and poll.startsAt >= :startInclusive
			  and poll.startsAt < :endExclusive
			""", Long.class)
			.setParameter("userId", userId)
			.setParameter("activeStatus", CampusMemberStatus.ACTIVE)
			.setParameter("startInclusive", startInclusive)
			.setParameter("endExclusive", endExclusive)
			.getSingleResult();
		return new CommentActivityRecapAggregate(commentCount);
	}

	@Override
	public PenaltySummaryRecapAggregate findPenaltySummary(
		Long userId,
		LocalDate startDate,
		LocalDate endDateExclusive
	) {
		long paidCount = 0;
		long paidAmount = 0;
		long unpaidCount = 0;
		long unpaidAmount = 0;
		List<Object[]> rows = entityManager.createQuery("""
			select charge.status, count(charge.id), coalesce(sum(charge.amount), 0)
			from ChargeItem charge, WeeklyDevotionRecord weekly
			where charge.sourceId = weekly.id
			  and charge.userId = weekly.userId
			  and charge.userId = :userId
			  and charge.paymentCategory = :paymentCategory
			  and charge.sourceType = :sourceType
			  and charge.status in :statuses
			  and exists (
			    select member.id from CampusMember member
			    where member.userId = :userId
			      and member.campusId = weekly.campusId
			      and member.status = :activeStatus
			  )
			  and weekly.weekStartDate >= :startDate
			  and weekly.weekStartDate < :endDateExclusive
			group by charge.status
			""", Object[].class)
			.setParameter("userId", userId)
			.setParameter("paymentCategory", PaymentCategory.PENALTY)
			.setParameter("sourceType", ChargeSourceType.DEVOTION_RECORD)
			.setParameter("statuses", List.of(ChargeStatus.PAID, ChargeStatus.UNPAID))
			.setParameter("activeStatus", CampusMemberStatus.ACTIVE)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.getResultList();
		for (Object[] row : rows) {
			ChargeStatus status = (ChargeStatus) row[0];
			long count = ((Number) row[1]).longValue();
			long amount = ((Number) row[2]).longValue();
			if (status == ChargeStatus.PAID) {
				paidCount = count;
				paidAmount = amount;
			} else if (status == ChargeStatus.UNPAID) {
				unpaidCount = count;
				unpaidAmount = amount;
			}
		}
		return new PenaltySummaryRecapAggregate(paidCount, paidAmount, unpaidCount, unpaidAmount);
	}
}
