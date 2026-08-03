package com.faithlog.user.infrastructure.repository;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.user.domain.type.YearlyRecapArchiveFactType;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class JpaYearlyRecapAggregateQueryAdapter implements YearlyRecapAggregateQueryPort {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final EntityManager entityManager;

	public JpaYearlyRecapAggregateQueryAdapter(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	@Override
	public boolean isCoverageComplete(int recapYear) {
		long coveredTypes = entityManager.createQuery("""
			select count(distinct coverage.factType)
			from YearlyRecapArchiveCoverage coverage
			where coverage.completeFromYear <= :recapYear
			""", Long.class)
			.setParameter("recapYear", recapYear)
			.getSingleResult();
		return coveredTypes == YearlyRecapArchiveFactType.values().length;
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
		@SuppressWarnings("unchecked")
		List<Object[]> dailyRows = entityManager.createNativeQuery("""
			select activity_date, quiet_time_checked, bible_reading_checked, prayer_checked
			from (
			  select daily.record_date as activity_date,
			         daily.quiet_time_checked, daily.bible_reading_checked, daily.prayer_checked,
			         daily.id as source_order
			  from devotion_daily_checks daily
			  join weekly_devotion_records weekly on weekly.id = daily.weekly_record_id
			  where weekly.user_id = :userId
			    and daily.record_date >= :startDate
			    and daily.record_date < :endDateExclusive
			    and not exists (
			      select 1 from yearly_recap_archive_facts fact
			      where fact.fact_type = 'DEVOTION_DAILY' and fact.source_id = daily.id
			    )
			  union all
			  select fact.activity_date, fact.flag_one, fact.flag_two, fact.flag_three, fact.source_id
			  from yearly_recap_archive_facts fact
			  where fact.fact_type = 'DEVOTION_DAILY'
			    and fact.user_id = :userId
			    and fact.recap_year = :recapYear
			) activity
			order by activity_date asc, source_order asc
			""")
			.setParameter("userId", userId)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.setParameter("recapYear", startDate.getYear())
			.getResultList();
		List<DevotionDailyActivity> dailyActivities = dailyRows.stream()
			.map(row -> new DevotionDailyActivity(
				toLocalDate(row[0]),
				(Boolean) row[1],
				(Boolean) row[2],
				(Boolean) row[3]
			))
			.toList();

		@SuppressWarnings("unchecked")
		List<Object> submittedRows = entityManager.createNativeQuery("""
			select weekly.week_start_date
			from weekly_devotion_records weekly
			where weekly.user_id = :userId
			  and weekly.submitted_at is not null
			  and weekly.week_start_date >= :startDate
			  and weekly.week_start_date < :endDateExclusive
			  and not exists (
			    select 1 from yearly_recap_archive_facts fact
			    where fact.fact_type = 'DEVOTION_WEEKLY' and fact.source_id = weekly.id
			  )
			union
			select fact.activity_date
			from yearly_recap_archive_facts fact
			where fact.fact_type = 'DEVOTION_WEEKLY'
			  and fact.user_id = :userId
			  and fact.recap_year = :recapYear
			  and fact.flag_one = true
			""")
			.setParameter("userId", userId)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.setParameter("recapYear", startDate.getYear())
			.getResultList();
		return new DevotionRecapSource(dailyActivities, submittedRows.size());
	}

	@Override
	public PrayerRecapAggregate findPrayer(Long userId, LocalDate startDate, LocalDate endDateExclusive) {
		@SuppressWarnings("unchecked")
		List<Object[]> rows = entityManager.createNativeQuery("""
			select week_start_date, season_id
			from (
			  select week.week_start_date, week.season_id
			  from prayer_submissions submission
			  join prayer_weeks week on week.id = submission.prayer_week_id
			  where submission.user_id = :userId
			    and submission.submitted_at is not null
			    and week.week_start_date >= :startDate
			    and week.week_start_date < :endDateExclusive
			    and not exists (
			      select 1 from yearly_recap_archive_facts fact
			      where fact.fact_type = 'PRAYER' and fact.source_id = submission.id
			    )
			  union
			  select fact.activity_date, fact.secondary_grouping_id
			  from yearly_recap_archive_facts fact
			  where fact.fact_type = 'PRAYER'
			    and fact.user_id = :userId
			    and fact.recap_year = :recapYear
			) prayer_activity
			""")
			.setParameter("userId", userId)
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.setParameter("recapYear", startDate.getYear())
			.getResultList();
		Set<LocalDate> weeks = new HashSet<>();
		Set<Long> seasons = new HashSet<>();
		rows.forEach(row -> {
			weeks.add(toLocalDate(row[0]));
			seasons.add(((Number) row[1]).longValue());
		});
		return new PrayerRecapAggregate(
			weeks.size(), seasons.size()
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
		Number commentCount = (Number) entityManager.createNativeQuery("""
			select count(*)
			from (
			  select comment.id
			  from poll_comments comment
			  join polls poll on poll.id = comment.poll_id
			  where comment.user_id = :userId
			    and comment.deleted_at is null
			    and comment.created_at >= :startInclusive
			    and comment.created_at < :endExclusive
			    and not exists (
			      select 1 from yearly_recap_archive_facts fact
			      where fact.fact_type = 'COMMENT' and fact.source_id = comment.id
			    )
			  union all
			  select fact.source_id
			  from yearly_recap_archive_facts fact
			  where fact.fact_type = 'COMMENT'
			    and fact.user_id = :userId
			    and fact.recap_year = :recapYear
			) comment_activity
			""")
			.setParameter("userId", userId)
			.setParameter("startInclusive", startInclusive)
			.setParameter("endExclusive", endExclusive)
			.setParameter("recapYear", startDate.getYear())
			.getSingleResult();
		return new CommentActivityRecapAggregate(commentCount.longValue());
	}

	@Override
	public PenaltySummaryRecapAggregate findPenaltySummary(
		Long userId,
		LocalDate startDate,
		LocalDate endDateExclusive
	) {
		@SuppressWarnings("unchecked")
		List<Object[]> rows = entityManager.createNativeQuery("""
			with archived as (
			  select fact.source_id, fact.status_value, fact.amount_value
			  from yearly_recap_archive_facts fact
			  where fact.fact_type = 'PENALTY'
			    and fact.user_id = :userId
			    and fact.recap_year = :recapYear
			), live_year as (
			  select charge.id as source_id, charge.status as status_value, charge.amount as amount_value
			  from charge_items charge
			  join weekly_devotion_records weekly on weekly.id = charge.source_id
			    and weekly.user_id = charge.user_id
			  where charge.user_id = :userId
			    and charge.payment_category = 'PENALTY'
			    and charge.source_type = 'DEVOTION_RECORD'
			    and charge.status in ('PAID', 'UNPAID')
			    and weekly.week_start_date >= :startDate
			    and weekly.week_start_date < :endDateExclusive
			), live_archived as (
			  select charge.id as source_id, charge.status as status_value, charge.amount as amount_value
			  from charge_items charge
			  join archived on archived.source_id = charge.id
			  where charge.user_id = :userId
			    and charge.payment_category = 'PENALTY'
			    and charge.source_type = 'DEVOTION_RECORD'
			), live_all as (
			  select * from live_year
			  union
			  select * from live_archived
			), resolved as (
			  select archived.source_id,
			         coalesce(live_all.status_value, archived.status_value) as status_value,
			         coalesce(live_all.amount_value, archived.amount_value) as amount_value
			  from archived
			  left join live_all on live_all.source_id = archived.source_id
			  union all
			  select live_year.source_id, live_year.status_value, live_year.amount_value
			  from live_year
			  where not exists (
			    select 1 from archived where archived.source_id = live_year.source_id
			  )
			)
			select status_value, count(*), coalesce(sum(amount_value), 0)
			from resolved
			where status_value in ('PAID', 'UNPAID')
			group by status_value
			""")
			.setParameter("userId", userId)
			.setParameter("recapYear", startDate.getYear())
			.setParameter("startDate", startDate)
			.setParameter("endDateExclusive", endDateExclusive)
			.getResultList();
		long paidCount = 0;
		long paidAmount = 0;
		long unpaidCount = 0;
		long unpaidAmount = 0;
		for (Object[] row : rows) {
			ChargeStatus status = ChargeStatus.valueOf((String) row[0]);
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

	private LocalDate toLocalDate(Object value) {
		if (value instanceof LocalDate localDate) {
			return localDate;
		}
		if (value instanceof java.sql.Date sqlDate) {
			return sqlDate.toLocalDate();
		}
		throw new IllegalArgumentException("unsupported date value");
	}
}
