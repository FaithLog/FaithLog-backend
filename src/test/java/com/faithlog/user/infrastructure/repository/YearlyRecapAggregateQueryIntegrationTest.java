package com.faithlog.user.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.devotion.domain.entity.DevotionDailyCheck;
import com.faithlog.devotion.domain.entity.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.repository.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.entity.PollComment;
import com.faithlog.poll.domain.entity.PollResponse;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.infrastructure.repository.PollCommentRepository;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.prayer.domain.entity.PrayerSeason;
import com.faithlog.prayer.domain.entity.PrayerSubmission;
import com.faithlog.prayer.domain.entity.PrayerWeek;
import com.faithlog.prayer.infrastructure.repository.PrayerSeasonRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.repository.PrayerWeekRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.port.DevotionRecapSource;
import com.faithlog.user.service.port.PollRecapAggregate;
import com.faithlog.user.service.port.PrayerRecapAggregate;
import com.faithlog.user.service.port.YearlyRecapAggregateQueryPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class YearlyRecapAggregateQueryIntegrationTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired private YearlyRecapAggregateQueryPort queryPort;
	@Autowired private UserRepository userRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private WeeklyDevotionRecordRepository weeklyRepository;
	@Autowired private DevotionDailyCheckRepository dailyRepository;
	@Autowired private PrayerSeasonRepository prayerSeasonRepository;
	@Autowired private PrayerWeekRepository prayerWeekRepository;
	@Autowired private PrayerSubmissionRepository prayerSubmissionRepository;
	@Autowired private PollRepository pollRepository;
	@Autowired private PollResponseRepository pollResponseRepository;
	@Autowired private PollCommentRepository pollCommentRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
	@Autowired private EntityManager entityManager;
	@Autowired private EntityManagerFactory entityManagerFactory;

	@Test
	void aggregates_without_private_content_and_uses_six_fixed_queries_with_one_thousand_members() {
		int year = 2026;
		LocalDate start = LocalDate.of(year, 1, 1);
		LocalDate end = start.plusYears(1);
		Instant startInstant = start.atStartOfDay(SEOUL).toInstant();
		Instant endInstant = end.atStartOfDay(SEOUL).toInstant();
		User target = userRepository.saveAndFlush(User.create("회고 대상", "recap-query@example.com", "hash"));
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"회고 캠퍼스", "서울", "고정 query", "RECAP-QUERY-236"
		));
		Campus secondCampus = campusRepository.saveAndFlush(Campus.create(
			"회고 두 번째 캠퍼스", "경기", "같은 달력 주차 중복", "RECAP-QUERY-236-SECOND"
		));
		insertOneThousandActiveMemberships(campus.id(), target.id());
		insertActiveMembership(secondCampus.id(), target.id());

		WeeklyDevotionRecord weekly = WeeklyDevotionRecord.create(campus.id(), target.id(), LocalDate.of(year, 1, 5));
		weekly.submit(Instant.parse("2027-01-10T00:00:00Z"));
		weeklyRepository.saveAndFlush(weekly);
		WeeklyDevotionRecord secondCampusWeekly = WeeklyDevotionRecord.create(
			secondCampus.id(), target.id(), LocalDate.of(year, 1, 5)
		);
		secondCampusWeekly.submit(Instant.parse("2027-01-10T00:00:00Z"));
		weeklyRepository.saveAndFlush(secondCampusWeekly);
		dailyRepository.saveAndFlush(DevotionDailyCheck.create(
			weekly.id(), LocalDate.of(year, 1, 6), true, true, false
		));

		PrayerSeason season = prayerSeasonRepository.saveAndFlush(PrayerSeason.create(
			campus.id(), "회고 기도 시즌", LocalDate.of(year, 1, 1), target.id()
		));
		PrayerWeek prayerWeek = prayerWeekRepository.saveAndFlush(PrayerWeek.create(
			campus.id(), season.id(), LocalDate.of(year, 12, 28)
		));
		prayerSubmissionRepository.saveAndFlush(PrayerSubmission.create(
			prayerWeek.id(), 99L, target.id(), "절대 응답에 포함하지 않을 기도 내용",
			target.id(), Instant.parse("2027-01-03T00:00:00Z")
		));
		PrayerSeason secondSeason = prayerSeasonRepository.saveAndFlush(PrayerSeason.create(
			secondCampus.id(), "회고 두 번째 기도 시즌", LocalDate.of(year, 1, 1), target.id()
		));
		PrayerWeek secondPrayerWeek = prayerWeekRepository.saveAndFlush(PrayerWeek.create(
			secondCampus.id(), secondSeason.id(), LocalDate.of(year, 12, 28)
		));
		prayerSubmissionRepository.saveAndFlush(PrayerSubmission.create(
			secondPrayerWeek.id(), 100L, target.id(), "응답에 포함하지 않을 두 번째 기도 내용",
			target.id(), Instant.parse("2027-01-03T00:00:00Z")
		));

		for (PollType pollType : PollType.values()) {
			Poll poll = createPoll(campus.id(), target.id(), pollType, startInstant.plusSeconds(3600));
			pollResponseRepository.save(PollResponse.create(poll.id(), target.id(), "비공개 메모"));
		}
		Poll commentedPoll = createPoll(campus.id(), target.id(), PollType.CUSTOM, startInstant.plusSeconds(7200));
		PollComment visibleComment = pollCommentRepository.save(PollComment.create(
			commentedPoll.id(), target.id(), "비공개 댓글"
		));
		PollComment deletedComment = pollCommentRepository.save(PollComment.create(
			commentedPoll.id(), target.id(), "삭제할 비공개 댓글"
		));
		deletedComment.delete();
		pollCommentRepository.saveAndFlush(deletedComment);
		assertThat(visibleComment.id()).isNotNull();
		entityManager.flush();
		entityManager.clear();

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();

		var campuses = queryPort.findActiveCampuses(target.id());
		DevotionRecapSource devotion = queryPort.findDevotion(target.id(), start, end);
		PrayerRecapAggregate prayer = queryPort.findPrayer(target.id(), start, end);
		PollRecapAggregate poll = queryPort.findPoll(target.id(), startInstant, endInstant);

		assertThat(campuses).hasSize(2);
		assertThat(devotion.dailyActivities()).singleElement().satisfies(day -> {
			assertThat(day.quietTimeChecked()).isTrue();
			assertThat(day.prayerChecked()).isTrue();
			assertThat(day.bibleReadingChecked()).isFalse();
		});
		assertThat(devotion.submittedWeekCount()).isEqualTo(1);
		assertThat(prayer.submittedWeekCount()).isEqualTo(1);
		assertThat(prayer.participatedSeasonCount()).isEqualTo(2);
		assertThat(poll.participatedCount()).isEqualTo(5);
		for (PollType pollType : PollType.values()) {
			assertThat(poll.count(pollType)).isEqualTo(1);
		}
		assertThat(poll.commentCount()).isEqualTo(1);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(6L);
	}

	private Poll createPoll(Long campusId, Long userId, PollType pollType, Instant startsAt) {
		return pollRepository.saveAndFlush(Poll.create(
			campusId, null, "회고 투표 " + pollType, pollType, SelectionType.SINGLE,
			false, false, ChargeGenerationType.NONE, null, null,
			startsAt, startsAt.plusSeconds(3600), userId
		));
	}

	private void insertOneThousandActiveMemberships(Long campusId, Long targetUserId) {
		Instant now = Instant.parse("2026-01-01T00:00:00Z");
		insertActiveMembership(campusId, targetUserId);
		List<Object[]> users = new ArrayList<>();
		List<Object[]> memberships = new ArrayList<>();
		for (int index = 1; index < 1000; index++) {
			long userId = 1_000_000L + index;
			users.add(new Object[]{
				userId, "회고 부하 " + index, "recap-load-" + index + "@example.com",
				"hash", "USER", true, 0L, now, now
			});
			memberships.add(new Object[]{campusId, userId, "MEMBER", "ACTIVE", now, now, now});
		}
		jdbcTemplate.batchUpdate("""
			insert into users
			(id, name, email, password_hash, role, is_active, token_version, created_at, updated_at)
			values (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""", users);
		jdbcTemplate.batchUpdate("""
			insert into campus_members
			(campus_id, user_id, campus_role, status, joined_at, created_at, updated_at)
			values (?, ?, ?, ?, ?, ?, ?)
			""", memberships);
	}

	private void insertActiveMembership(Long campusId, Long userId) {
		Instant joinedAt = Instant.parse("2026-01-01T00:00:00Z");
		jdbcTemplate.update("""
			insert into campus_members
			(campus_id, user_id, campus_role, status, joined_at, created_at, updated_at)
			values (?, ?, 'MEMBER', 'ACTIVE', ?, ?, ?)
			""", campusId, userId, joinedAt, joinedAt, joinedAt);
	}
}
