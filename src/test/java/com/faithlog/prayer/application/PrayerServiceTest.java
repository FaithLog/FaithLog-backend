package com.faithlog.prayer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.campus.application.CampusCreateResult;
import com.faithlog.campus.application.CampusService;
import com.faithlog.campus.application.ChangeCampusRoleCommand;
import com.faithlog.campus.application.CreateCampusCommand;
import com.faithlog.campus.application.JoinCampusCommand;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.PrayerGroupMember;
import com.faithlog.prayer.infrastructure.jpa.PrayerGroupMemberRepository;
import com.faithlog.prayer.infrastructure.jpa.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.jpa.PrayerWeekRepository;
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
class PrayerServiceTest {

	@Autowired
	private PrayerService prayerService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private PrayerWeekRepository prayerWeekRepository;

	@Autowired
	private PrayerSubmissionRepository prayerSubmissionRepository;

	@Autowired
	private PrayerGroupMemberRepository prayerGroupMemberRepository;

	@Test
	void active_campus_member_reads_all_groups_without_creating_week_or_submissions() {
		PrayerFixture fixture = createFixture("read-all");

		PrayerWeekBoardResult board = prayerService.getWeeklyBoard(
			fixture.campusId(),
			LocalDate.of(2026, 6, 22),
			fixture.memberA().id()
		);

		assertThat(board.campusId()).isEqualTo(fixture.campusId());
		assertThat(board.weekStartDate()).isEqualTo(LocalDate.of(2026, 6, 22));
		assertThat(board.weekEndDate()).isEqualTo(LocalDate.of(2026, 6, 28));
		assertThat(board.status()).isEqualTo("OPEN");
		assertThat(board.submittedCount()).isZero();
		assertThat(board.targetMemberCount()).isEqualTo(3);
		assertThat(board.groups()).hasSize(2);
		assertThat(board.groups()).flatExtracting(PrayerGroupBoardResult::members)
			.extracting(PrayerMemberSubmissionResult::userId)
			.containsExactly(fixture.memberA().id(), fixture.memberB().id(), fixture.memberC().id());
		assertThat(board.groups()).flatExtracting(PrayerGroupBoardResult::members)
			.allSatisfy(member -> {
				assertThat(member.submissionId()).isNull();
				assertThat(member.content()).isNull();
				assertThat(member.version()).isZero();
				assertThat(member.submittedAt()).isNull();
			});
		assertThat(prayerWeekRepository.count()).isZero();
		assertThat(prayerSubmissionRepository.count()).isZero();
	}

	@Test
	void put_creates_missing_week_and_member_submissions_with_nullable_content_and_counts_written_rows() {
		PrayerFixture fixture = createFixture("put-create");
		LocalDate nextMonday = LocalDate.of(2026, 6, 29);

		PrayerWeekBoardResult saved = prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			nextMonday,
			fixture.memberA().id(),
			List.of(
				new PrayerSubmissionCommand(fixture.memberA().id(), "기도제목 A", 0),
				new PrayerSubmissionCommand(fixture.memberB().id(), null, 0)
			)
		));

		assertThat(prayerWeekRepository.count()).isEqualTo(1);
		assertThat(prayerSubmissionRepository.count()).isEqualTo(2);
		assertThat(saved.submittedCount()).isEqualTo(2);
		assertThat(saved.targetMemberCount()).isEqualTo(3);
		assertThat(saved.groups()).flatExtracting(PrayerGroupBoardResult::members)
			.filteredOn(member -> member.userId().equals(fixture.memberA().id()))
			.singleElement()
			.satisfies(member -> {
				assertThat(member.content()).isEqualTo("기도제목 A");
				assertThat(member.version()).isEqualTo(1);
				assertThat(member.submittedAt()).isNotNull();
			});
		assertThat(saved.groups()).flatExtracting(PrayerGroupBoardResult::members)
			.filteredOn(member -> member.userId().equals(fixture.memberB().id()))
			.singleElement()
			.satisfies(member -> {
				assertThat(member.content()).isNull();
				assertThat(member.version()).isEqualTo(1);
				assertThat(member.submittedAt()).isNotNull();
			});
	}

	@Test
	void normal_member_can_save_own_active_group_only_and_other_group_is_forbidden() {
		PrayerFixture fixture = createFixture("member-auth");
		LocalDate weekStart = LocalDate.of(2026, 6, 22);

		prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			weekStart,
			fixture.memberA().id(),
			List.of(new PrayerSubmissionCommand(fixture.memberB().id(), "같은 조", 0))
		));

		assertThat(prayerSubmissionRepository.count()).isEqualTo(1);
		assertThatThrownBy(() -> prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			weekStart,
			fixture.memberA().id(),
			List.of(new PrayerSubmissionCommand(fixture.memberC().id(), "다른 조", 0))
		)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRAYER_SUBMISSION_FORBIDDEN);
	}

	@Test
	void campus_managers_and_service_admin_can_save_all_groups() {
		PrayerFixture fixture = createFixture("manager-auth");
		updateCampusRole(fixture.campusId(), fixture.memberB().id(), CampusRole.CAMPUS_LEADER, fixture.manager().id());
		User admin = saveUser("prayer-manager-auth-admin@example.com", UserRole.ADMIN);

		prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			LocalDate.of(2026, 6, 22),
			fixture.memberB().id(),
			List.of(new PrayerSubmissionCommand(fixture.memberC().id(), "캠퍼스 리더 저장", 0))
		));
		PrayerWeekBoardResult savedByAdmin = prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			LocalDate.of(2026, 6, 29),
			admin.id(),
			List.of(new PrayerSubmissionCommand(fixture.memberA().id(), "서비스 관리자 저장", 0))
		));

		assertThat(savedByAdmin.submittedCount()).isEqualTo(1);
		assertThat(prayerSubmissionRepository.count()).isEqualTo(2);
	}

	@Test
	void matching_version_updates_content_and_conflicting_version_rolls_back_all_changes() {
		PrayerFixture fixture = createFixture("version");
		LocalDate weekStart = LocalDate.of(2026, 6, 22);
		prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			weekStart,
			fixture.manager().id(),
			List.of(
				new PrayerSubmissionCommand(fixture.memberA().id(), "A-1", 0),
				new PrayerSubmissionCommand(fixture.memberC().id(), "C-1", 0)
			)
		));

		PrayerWeekBoardResult updated = prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			weekStart,
			fixture.manager().id(),
			List.of(new PrayerSubmissionCommand(fixture.memberA().id(), "A-2", 1))
		));

		assertThat(updated.groups()).flatExtracting(PrayerGroupBoardResult::members)
			.filteredOn(member -> member.userId().equals(fixture.memberA().id()))
			.singleElement()
			.satisfies(member -> {
				assertThat(member.content()).isEqualTo("A-2");
				assertThat(member.version()).isEqualTo(2);
			});

		assertThatThrownBy(() -> prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			weekStart,
			fixture.manager().id(),
			List.of(
				new PrayerSubmissionCommand(fixture.memberA().id(), "A-conflict", 1),
				new PrayerSubmissionCommand(fixture.memberB().id(), "B-should-rollback", 0)
			)
		)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRAYER_SUBMISSION_CONFLICT);

		PrayerWeekBoardResult afterConflict = prayerService.getWeeklyBoard(fixture.campusId(), weekStart, fixture.manager().id());
		assertThat(afterConflict.groups()).flatExtracting(PrayerGroupBoardResult::members)
			.filteredOn(member -> member.userId().equals(fixture.memberA().id()))
			.singleElement()
			.extracting(PrayerMemberSubmissionResult::content)
			.isEqualTo("A-2");
		assertThat(afterConflict.groups()).flatExtracting(PrayerGroupBoardResult::members)
			.filteredOn(member -> member.userId().equals(fixture.memberB().id()))
			.singleElement()
			.satisfies(member -> {
				assertThat(member.submissionId()).isNull();
				assertThat(member.content()).isNull();
				assertThat(member.version()).isZero();
			});
		assertThat(prayerSubmissionRepository.count()).isEqualTo(2);
	}

	@Test
	void active_season_cannot_be_duplicated_and_group_members_replace_all_with_inactive_and_reactivate() {
		PrayerFixture fixture = createFixture("replace");

		assertThatThrownBy(() -> prayerService.createSeason(new CreatePrayerSeasonCommand(
			fixture.campusId(),
			fixture.manager().id(),
			"중복 활성 시즌",
			LocalDate.of(2026, 7, 1)
		)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRAYER_ACTIVE_SEASON_ALREADY_EXISTS);

		PrayerGroupResult group = prayerService.replaceGroupMembers(new ReplacePrayerGroupMembersCommand(
			fixture.groupAId(),
			fixture.manager().id(),
			List.of(fixture.memberB().id(), fixture.memberC().id())
		));

		assertThat(group.members()).extracting(PrayerGroupMemberResult::userId)
			.containsExactly(fixture.memberB().id(), fixture.memberC().id());
		assertThat(prayerGroupMemberRepository.findByGroupIdOrderByIdAsc(fixture.groupAId()))
			.filteredOn(member -> member.userId().equals(fixture.memberA().id()))
			.singleElement()
			.extracting(PrayerGroupMember::isActive)
			.isEqualTo(false);

		prayerService.replaceGroupMembers(new ReplacePrayerGroupMembersCommand(
			fixture.groupAId(),
			fixture.manager().id(),
			List.of(fixture.memberA().id(), fixture.memberC().id())
		));

		assertThat(prayerGroupMemberRepository.findByGroupIdOrderByIdAsc(fixture.groupAId()))
			.filteredOn(member -> member.userId().equals(fixture.memberA().id()))
			.singleElement()
			.extracting(PrayerGroupMember::isActive)
			.isEqualTo(true);
	}

	@Test
	void week_start_date_must_be_monday_but_future_monday_is_writable() {
		PrayerFixture fixture = createFixture("week-date");

		assertThatThrownBy(() -> prayerService.getWeeklyBoard(
			fixture.campusId(),
			LocalDate.of(2026, 6, 23),
			fixture.memberA().id()
		))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.PRAYER_INVALID_WEEK_START_DATE);

		PrayerWeekBoardResult board = prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			LocalDate.of(2026, 6, 29),
			fixture.memberA().id(),
			List.of(new PrayerSubmissionCommand(fixture.memberA().id(), "다음 월요일 주차", 0))
		));

		assertThat(board.weekStartDate()).isEqualTo(LocalDate.of(2026, 6, 29));
		assertThat(board.submittedCount()).isEqualTo(1);
	}

	private PrayerFixture createFixture(String suffix) {
		User manager = saveUser("prayer-" + suffix + "-manager@example.com", UserRole.MANAGER);
		User memberA = saveUser("prayer-" + suffix + "-a@example.com", UserRole.USER);
		User memberB = saveUser("prayer-" + suffix + "-b@example.com", UserRole.USER);
		User memberC = saveUser("prayer-" + suffix + "-c@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"기도 " + suffix,
			"분당",
			"기도 테스트 캠퍼스"
		));
		campusService.joinCampus(new JoinCampusCommand(memberA.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(memberB.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(memberC.id(), campus.inviteCode()));
		PrayerSeasonResult season = prayerService.createSeason(new CreatePrayerSeasonCommand(
			campus.campusId(),
			manager.id(),
			"2026 여름",
			LocalDate.of(2026, 6, 1)
		));
		PrayerGroupResult groupA = prayerService.createGroup(new CreatePrayerGroupCommand(
			season.seasonId(),
			manager.id(),
			"1조",
			1
		));
		PrayerGroupResult groupB = prayerService.createGroup(new CreatePrayerGroupCommand(
			season.seasonId(),
			manager.id(),
			"2조",
			2
		));
		prayerService.replaceGroupMembers(new ReplacePrayerGroupMembersCommand(
			groupA.groupId(),
			manager.id(),
			List.of(memberA.id(), memberB.id())
		));
		prayerService.replaceGroupMembers(new ReplacePrayerGroupMembersCommand(
			groupB.groupId(),
			manager.id(),
			List.of(memberC.id())
		));
		return new PrayerFixture(campus.campusId(), manager, memberA, memberB, memberC, groupA.groupId(), groupB.groupId());
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create(email.substring(0, email.indexOf('@')), email, "{noop}password");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.save(user);
	}

	private void updateCampusRole(Long campusId, Long userId, CampusRole role, Long requesterId) {
		Long membershipId = campusMemberRepository.findByCampusIdAndUserId(campusId, userId)
			.orElseThrow()
			.id();
		campusService.changeCampusRole(new ChangeCampusRoleCommand(campusId, membershipId, requesterId, role));
	}

	private record PrayerFixture(
		Long campusId,
		User manager,
		User memberA,
		User memberB,
		User memberC,
		Long groupAId,
		Long groupBId
	) {
	}
}
