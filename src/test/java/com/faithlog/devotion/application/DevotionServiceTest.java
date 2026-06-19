package com.faithlog.devotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.application.CampusCreateResult;
import com.faithlog.campus.application.CampusService;
import com.faithlog.campus.application.CreateCampusCommand;
import com.faithlog.campus.application.JoinCampusCommand;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.devotion.domain.DevotionDailyCheck;
import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.jpa.DevotionDailyCheckRepository;
import com.faithlog.devotion.infrastructure.jpa.WeeklyDevotionRecordRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DevotionServiceTest {

	@Autowired
	private DevotionService devotionService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private WeeklyDevotionRecordRepository weeklyRecordRepository;

	@Autowired
	private DevotionDailyCheckRepository dailyCheckRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Test
	void updateDailyCheck_creates_daily_and_weekly_rows_without_submission_or_charge() {
		User manager = saveUser("devotion-daily-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "60캠");
		User member = saveUser("devotion-daily-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		LocalDate recordDate = LocalDate.of(2026, 6, 17);

		DailyDevotionResult result = devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			member.id(),
			recordDate,
			true,
			true,
			false
		));

		assertThat(result.recordDate()).isEqualTo(recordDate);
		assertThat(result.quietTimeChecked()).isTrue();
		assertThat(result.prayerChecked()).isTrue();
		assertThat(result.bibleReadingChecked()).isFalse();
		assertThat(result.quietTimeCount()).isEqualTo(1);
		assertThat(result.prayerCount()).isEqualTo(1);
		assertThat(result.bibleReadingCount()).isEqualTo(0);
		assertThat(result.submittedAt()).isNull();
		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), LocalDate.of(2026, 6, 15))
			.orElseThrow();
		assertThat(weeklyRecord.submittedAt()).isNull();
		assertThat(dailyCheckRepository.findByWeeklyRecordIdAndRecordDate(weeklyRecord.id(), recordDate)).isPresent();
		assertThat(chargeItemRepository.count()).isZero();

		devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			member.id(),
			recordDate,
			false,
			true,
			true
		));

		assertThat(dailyCheckRepository.findByWeeklyRecordIdAndRecordDate(weeklyRecord.id(), recordDate))
			.get()
			.satisfies(check -> {
				assertThat(check.quietTimeChecked()).isFalse();
				assertThat(check.prayerChecked()).isTrue();
				assertThat(check.bibleReadingChecked()).isTrue();
			});
		assertThat(weeklyRecordRepository.findById(weeklyRecord.id()))
			.get()
			.satisfies(saved -> {
				assertThat(saved.submittedAt()).isNull();
				assertThat(saved.quietTimeCount()).isZero();
				assertThat(saved.prayerCount()).isEqualTo(1);
				assertThat(saved.bibleReadingCount()).isEqualTo(1);
			});
		assertThat(chargeItemRepository.count()).isZero();
	}

	@Test
	void updateWeeklyCheck_creates_seven_daily_rows_fills_missing_false_and_updates_submission_summary() {
		User manager = saveUser("devotion-weekly-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "61캠");
		User member = saveUser("devotion-weekly-member@example.com", UserRole.USER);
		joinCampus(campus, member);
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		WeeklyDevotionResult result = devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			weekStartDate,
			List.of(
				new DevotionDailyCheckCommand(weekStartDate, true, true, false),
				new DevotionDailyCheckCommand(weekStartDate.plusDays(2), true, false, true)
			),
			5,
			true
		));

		assertThat(result.weekStartDate()).isEqualTo(weekStartDate);
		assertThat(result.weekEndDate()).isEqualTo(LocalDate.of(2026, 6, 21));
		assertThat(result.quietTimeCount()).isEqualTo(2);
		assertThat(result.prayerCount()).isEqualTo(1);
		assertThat(result.bibleReadingCount()).isEqualTo(1);
		assertThat(result.saturdayLateMinutes()).isEqualTo(5);
		assertThat(result.submittedAt()).isNotNull();
		assertThat(result.dailyChecks()).hasSize(7);

		WeeklyDevotionRecord weeklyRecord = weeklyRecordRepository
			.findByCampusIdAndUserIdAndWeekStartDate(campus.campusId(), member.id(), weekStartDate)
			.orElseThrow();
		List<DevotionDailyCheck> dailyChecks = dailyCheckRepository.findByWeeklyRecordIdOrderByRecordDateAsc(weeklyRecord.id());
		assertThat(dailyChecks).hasSize(7);
		assertThat(dailyChecks)
			.filteredOn(check -> check.recordDate().equals(weekStartDate.plusDays(1)))
			.singleElement()
			.satisfies(check -> {
				assertThat(check.quietTimeChecked()).isFalse();
				assertThat(check.prayerChecked()).isFalse();
				assertThat(check.bibleReadingChecked()).isFalse();
			});
		assertThat(chargeItemRepository.count()).isZero();
	}

	@Test
	void getMyWeeklyCheck_uses_requester_identity_and_adminMissing_uses_submittedAt() {
		User manager = saveUser("devotion-missing-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "62캠");
		User submittedMember = saveUser("devotion-submitted-member@example.com", UserRole.USER);
		User unsubmittedMember = saveUser("devotion-unsubmitted-member@example.com", UserRole.USER);
		User noRecordMember = saveUser("devotion-no-record-member@example.com", UserRole.USER);
		joinCampus(campus, submittedMember);
		joinCampus(campus, unsubmittedMember);
		joinCampus(campus, noRecordMember);
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			manager.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, true, true, true)),
			0,
			true
		));
		devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			submittedMember.id(),
			weekStartDate,
			List.of(new DevotionDailyCheckCommand(weekStartDate, true, true, true)),
			0,
			true
		));
		devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			unsubmittedMember.id(),
			weekStartDate,
			true,
			true,
			true
		));

		WeeklyDevotionResult myWeek = devotionService.getMyWeeklyCheck(new GetMyWeeklyDevotionQuery(
			campus.campusId(),
			submittedMember.id(),
			weekStartDate
		));
		List<MissingDevotionMemberResult> missingMembers = devotionService.getMissingMembers(new GetMissingDevotionMembersQuery(
			campus.campusId(),
			manager.id(),
			weekStartDate
		));

		assertThat(myWeek.userId()).isEqualTo(submittedMember.id());
		assertThat(myWeek.submittedAt()).isNotNull();
		assertThat(missingMembers)
			.extracting(MissingDevotionMemberResult::userId)
			.containsExactly(unsubmittedMember.id(), noRecordMember.id());
	}

	@Test
	void weeklyCheck_rejects_non_monday_and_devotion_apis_require_active_campus_member() {
		User manager = saveUser("devotion-auth-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "63캠");
		User member = saveUser("devotion-auth-member@example.com", UserRole.USER);
		User outsider = saveUser("devotion-auth-outsider@example.com", UserRole.USER);
		joinCampus(campus, member);

		assertThatThrownBy(() -> devotionService.updateWeeklyCheck(new UpdateWeeklyDevotionCommand(
			campus.campusId(),
			member.id(),
			LocalDate.of(2026, 6, 16),
			List.of(),
			0,
			true
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("weekStartDate는 월요일이어야 합니다.");

		assertThatThrownBy(() -> devotionService.updateDailyCheck(new UpdateDailyDevotionCommand(
			campus.campusId(),
			outsider.id(),
			LocalDate.of(2026, 6, 17),
			true,
			true,
			true
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("경건생활 접근 권한이 없습니다.");
	}

	@Test
	void adminMissing_requires_campus_manager_or_service_admin() {
		User manager = saveUser("devotion-admin-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "64캠");
		User normalMember = saveUser("devotion-admin-normal@example.com", UserRole.USER);
		User leader = saveUser("devotion-admin-leader@example.com", UserRole.USER);
		User admin = saveUser("devotion-admin-service@example.com", UserRole.ADMIN);
		joinCampus(campus, normalMember);
		joinCampus(campus, leader);
		updateCampusRole(campus.campusId(), leader.id(), CampusRole.CAMPUS_LEADER);
		LocalDate weekStartDate = LocalDate.of(2026, 6, 15);

		assertThatThrownBy(() -> devotionService.getMissingMembers(new GetMissingDevotionMembersQuery(
			campus.campusId(),
			normalMember.id(),
			weekStartDate
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("경건생활 관리자 권한이 없습니다.");

		assertThat(devotionService.getMissingMembers(new GetMissingDevotionMembersQuery(
			campus.campusId(),
			leader.id(),
			weekStartDate
		))).isNotEmpty();
		assertThat(devotionService.getMissingMembers(new GetMissingDevotionMembersQuery(
			campus.campusId(),
			admin.id(),
			weekStartDate
		))).isNotEmpty();
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			name,
			"분당",
			"분당 " + name
		));
	}

	private void joinCampus(CampusCreateResult campus, User user) {
		campusService.joinCampus(new JoinCampusCommand(user.id(), campus.inviteCode()));
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.saveAndFlush(User.create("경건테스트", email, "encoded-password"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private void updateCampusRole(Long campusId, Long userId, CampusRole campusRole) {
		CampusMember member = campusMemberRepository.findByCampusIdAndUserId(campusId, userId).orElseThrow();
		ReflectionTestUtils.setField(member, "campusRole", campusRole);
		campusMemberRepository.saveAndFlush(member);
	}
}
