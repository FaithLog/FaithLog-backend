package com.faithlog.user.infrastructure.repository;

import com.faithlog.batch.service.port.YearlyRecapArchivePort;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.user.domain.entity.YearlyRecapArchiveFact;
import com.faithlog.user.domain.type.YearlyRecapArchiveFactType;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Repository;

@Repository
public class YearlyRecapArchiveAdapter implements YearlyRecapArchivePort {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final EntityManager entityManager;
	private final YearlyRecapArchiveFactRepository factRepository;

	public YearlyRecapArchiveAdapter(
		EntityManager entityManager,
		YearlyRecapArchiveFactRepository factRepository
	) {
		this.entityManager = entityManager;
		this.factRepository = factRepository;
	}

	@Override
	public void archiveExpiredPolls(List<Long> pollIds) {
		if (pollIds.isEmpty()) {
			return;
		}
		List<Object[]> rows = entityManager.createQuery("""
			select comment.id, comment.userId, poll.campusId, poll.startsAt
			from PollComment comment, Poll poll
			where comment.pollId = poll.id
			  and poll.id in :pollIds
			  and comment.deletedAt is null
			  and exists (
			    select member.id from CampusMember member
			    where member.userId = comment.userId
			      and member.campusId = poll.campusId
			      and member.status = :activeStatus
			  )
			order by comment.id asc
			""", Object[].class)
			.setParameter("pollIds", pollIds)
			.setParameter("activeStatus", CampusMemberStatus.ACTIVE)
			.getResultList();
		saveMissing(YearlyRecapArchiveFactType.COMMENT, rows, row -> {
			Instant startsAt = (Instant) row[3];
			return YearlyRecapArchiveFact.comment(
				(Long) row[0], (Long) row[1], startsAt.atZone(SEOUL).getYear(), (Long) row[2]);
		});
	}

	@Override
	public void archivePrayerSubmissionsBefore(Instant createdAtCutoff) {
		List<Object[]> rows = entityManager.createQuery("""
			select submission.id, submission.userId, week.campusId, week.weekStartDate,
			       week.id, week.seasonId
			from PrayerSubmission submission, PrayerWeek week
			where submission.prayerWeekId = week.id
			  and submission.submittedAt is not null
			  and submission.createdAt < :createdAtCutoff
			order by submission.id asc
			""", Object[].class)
			.setParameter("createdAtCutoff", createdAtCutoff)
			.getResultList();
		saveMissing(YearlyRecapArchiveFactType.PRAYER, rows, row -> {
			LocalDate weekStartDate = (LocalDate) row[3];
			return YearlyRecapArchiveFact.prayer(
				(Long) row[0], (Long) row[1], weekStartDate.getYear(), (Long) row[2], weekStartDate,
				(Long) row[4], (Long) row[5]);
		});
	}

	@Override
	public void archiveAnnualRecapFacts(LocalDate startDate, LocalDate endDateExclusive) {
		archiveDevotionDaily(startDate, endDateExclusive);
		archiveDevotionWeekly(startDate, endDateExclusive);
		archivePenalties(startDate, endDateExclusive);
	}

	private void archiveDevotionDaily(LocalDate startDate, LocalDate endDateExclusive) {
		List<Object[]> rows = entityManager.createQuery("""
			select daily.id, weekly.userId, weekly.campusId, daily.recordDate,
			       daily.quietTimeChecked, daily.bibleReadingChecked, daily.prayerChecked
			from DevotionDailyCheck daily, WeeklyDevotionRecord weekly
			where daily.weeklyRecordId = weekly.id
			  and daily.recordDate >= :startDate
			  and daily.recordDate < :endDateExclusive
			order by daily.id asc
			""", Object[].class)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.getResultList();
		saveMissing(YearlyRecapArchiveFactType.DEVOTION_DAILY, rows, row -> {
			LocalDate recordDate = (LocalDate) row[3];
			return YearlyRecapArchiveFact.devotionDaily(
				(Long) row[0], (Long) row[1], recordDate.getYear(), (Long) row[2], recordDate,
				(Boolean) row[4], (Boolean) row[5], (Boolean) row[6]);
		});
	}

	private void archiveDevotionWeekly(LocalDate startDate, LocalDate endDateExclusive) {
		List<Object[]> rows = entityManager.createQuery("""
			select weekly.id, weekly.userId, weekly.campusId, weekly.weekStartDate, weekly.submittedAt
			from WeeklyDevotionRecord weekly
			where weekly.weekStartDate >= :startDate
			  and weekly.weekStartDate < :endDateExclusive
			order by weekly.id asc
			""", Object[].class)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.getResultList();
		saveMissing(YearlyRecapArchiveFactType.DEVOTION_WEEKLY, rows, row -> {
			LocalDate weekStartDate = (LocalDate) row[3];
			return YearlyRecapArchiveFact.devotionWeekly(
				(Long) row[0], (Long) row[1], weekStartDate.getYear(), (Long) row[2], weekStartDate,
				row[4] != null);
		});
	}

	private void archivePenalties(LocalDate startDate, LocalDate endDateExclusive) {
		List<Object[]> rows = entityManager.createQuery("""
			select charge.id, charge.userId, weekly.campusId, weekly.weekStartDate,
			       charge.status, charge.amount
			from ChargeItem charge, WeeklyDevotionRecord weekly
			where charge.sourceId = weekly.id
			  and charge.userId = weekly.userId
			  and charge.paymentCategory = :paymentCategory
			  and charge.sourceType = :sourceType
			  and charge.status in :statuses
			  and weekly.weekStartDate >= :startDate
			  and weekly.weekStartDate < :endDateExclusive
			  and exists (
			    select member.id from CampusMember member
			    where member.userId = charge.userId
			      and member.campusId = weekly.campusId
			      and member.status = :activeStatus
			  )
			order by charge.id asc
			""", Object[].class)
			.setParameter("paymentCategory", PaymentCategory.PENALTY)
			.setParameter("sourceType", ChargeSourceType.DEVOTION_RECORD)
			.setParameter("statuses", List.of(ChargeStatus.PAID, ChargeStatus.UNPAID))
			.setParameter("activeStatus", CampusMemberStatus.ACTIVE)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.getResultList();
		saveMissing(YearlyRecapArchiveFactType.PENALTY, rows, row -> {
			LocalDate weekStartDate = (LocalDate) row[3];
			return YearlyRecapArchiveFact.penalty(
				(Long) row[0], (Long) row[1], weekStartDate.getYear(), (Long) row[2],
				((ChargeStatus) row[4]).name(), ((Number) row[5]).longValue());
		});
	}

	private void saveMissing(
		YearlyRecapArchiveFactType factType,
		List<Object[]> rows,
		Function<Object[], YearlyRecapArchiveFact> mapper
	) {
		if (rows.isEmpty()) {
			return;
		}
		List<Long> sourceIds = rows.stream().map(row -> (Long) row[0]).toList();
		Set<Long> existing = new HashSet<>(factRepository.findExistingSourceIds(factType, sourceIds));
		List<YearlyRecapArchiveFact> missing = rows.stream()
			.filter(row -> !existing.contains((Long) row[0]))
			.map(mapper)
			.toList();
		if (!missing.isEmpty()) {
			factRepository.saveAll(missing);
			factRepository.flush();
		}
	}
}
