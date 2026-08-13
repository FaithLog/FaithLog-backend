package com.faithlog.shepherd.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.shepherd.domain.entity.ShepherdGroup;
import com.faithlog.shepherd.domain.entity.ShepherdGroupAssignee;
import com.faithlog.shepherd.domain.entity.WeeklyShepherdAttendanceReport;
import com.faithlog.shepherd.domain.type.WeeklyShepherdAttendanceStatus;
import com.faithlog.shepherd.infrastructure.repository.ShepherdGroupAssigneeRepository;
import com.faithlog.shepherd.infrastructure.repository.ShepherdGroupRepository;
import com.faithlog.shepherd.infrastructure.repository.WeeklyShepherdAttendanceReportRepository;
import com.faithlog.shepherd.service.command.CreateShepherdGroupCommand;
import com.faithlog.shepherd.service.command.ReplaceShepherdGroupAssigneesCommand;
import com.faithlog.shepherd.service.command.SaveShepherdAttendanceCommand;
import com.faithlog.shepherd.service.command.UpdateShepherdGroupCommand;
import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardGroupResult;
import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardResult;
import com.faithlog.shepherd.service.result.ShepherdAttendanceReportResult;
import com.faithlog.shepherd.service.result.ShepherdGroupResult;
import com.faithlog.shepherd.service.result.ShepherdHomeCardResult;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
	"spring.jpa.properties.hibernate.generate_statistics=true",
	"spring.jpa.properties.hibernate.session_factory.statement_inspector=com.faithlog.shepherd.service.ShepherdSqlCounterStatementInspector"
})
@Transactional
class ShepherdServiceTest {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
	private static final MutableClock TEST_CLOCK = new MutableClock(Instant.parse("2026-08-16T00:00:00Z"));

	@Autowired
	private ShepherdService shepherdService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private ShepherdGroupRepository shepherdGroupRepository;

	@Autowired
	private ShepherdGroupAssigneeRepository shepherdGroupAssigneeRepository;

	@Autowired
	private WeeklyShepherdAttendanceReportRepository shepherdAttendanceReportRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Test
	void member_creates_group_with_self_assignment_and_duplicate_name_does_not_grant_access() {
		Fixture fixture = createFixture("self-create");

		ShepherdGroupResult created = shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.memberA().id(),
			"  믿음   목장  ",
			List.of()
		));

		assertThat(created.name()).isEqualTo("믿음 목장");
		assertThat(created.assignees()).extracting("userId").containsExactly(fixture.memberA().id());
		assertThat(shepherdService.getMyGroups(fixture.campus().id(), fixture.memberA().id()))
			.extracting(ShepherdGroupResult::groupId)
			.containsExactly(created.groupId());
		assertThat(shepherdService.getMyGroups(fixture.campus().id(), fixture.memberB().id())).isEmpty();
		assertThatThrownBy(() -> shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.memberB().id(),
			"믿음 목장",
			List.of()
		)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.SHEPHERD_GROUP_DUPLICATE);
		assertThat(shepherdService.getMyGroups(fixture.campus().id(), fixture.memberB().id())).isEmpty();
	}

	@Test
	void direct_group_commands_report_invalid_name_as_validation_error() {
		Fixture fixture = createFixture("invalid-name");
		ShepherdGroupResult created = shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.memberA().id(),
			"정상 목장",
			List.of()
		));

		assertThatThrownBy(() -> shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.memberA().id(),
			"가".repeat(101),
			List.of()
		)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.GLOBAL_VALIDATION_FAILED);
		assertThatThrownBy(() -> shepherdService.updateGroup(new UpdateShepherdGroupCommand(
			fixture.campus().id(),
			created.groupId(),
			fixture.manager().id(),
			"나".repeat(101),
			created.version()
		)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.GLOBAL_VALIDATION_FAILED);
	}

	@Test
	void manager_creates_group_with_multiple_same_campus_active_assignees_and_rejects_last_assignee_removal() {
		Fixture fixture = createFixture("manager-assignees");

		ShepherdGroupResult created = shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.manager().id(),
			"소망 목장",
			List.of(fixture.memberA().id(), fixture.memberB().id())
		));

		assertThat(created.assignees()).extracting("userId")
			.containsExactly(fixture.memberA().id(), fixture.memberB().id());
		ShepherdGroupResult replaced = shepherdService.replaceAssignees(new ReplaceShepherdGroupAssigneesCommand(
			fixture.campus().id(),
			created.groupId(),
			fixture.manager().id(),
			List.of(fixture.memberB().id())
		));
		assertThat(replaced.assignees()).extracting("userId").containsExactly(fixture.memberB().id());
		assertThatThrownBy(() -> shepherdService.replaceAssignees(new ReplaceShepherdGroupAssigneesCommand(
			fixture.campus().id(),
			created.groupId(),
			fixture.manager().id(),
			List.of()
		)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.SHEPHERD_GROUP_ASSIGNEE_REQUIRED);
	}

	@Test
	void assignee_saves_sunday_counts_and_stale_version_is_rejected() {
		Fixture fixture = createFixture("attendance-save");
		ShepherdGroupResult group = shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.memberA().id(),
			"사랑 목장",
			List.of()
		));
		LocalDate sunday = LocalDate.of(2026, 8, 16);

		ShepherdAttendanceReportResult draft = shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
			fixture.campus().id(),
			group.groupId(),
			sunday,
			fixture.memberA().id(),
			3,
			4,
			1,
			"첫 입력",
			"DRAFT",
			0
		));

		assertThat(draft.smallGroupMeetingCount()).isEqualTo(3);
		assertThat(draft.holyWaveCount()).isEqualTo(4);
		assertThat(draft.otherWorshipCount()).isEqualTo(1);
		assertThat(draft.status()).isEqualTo("DRAFT");
		assertThat(draft.version()).isEqualTo(1);
		assertThat(draft.lastModifiedByUserId()).isEqualTo(fixture.memberA().id());
		assertThatThrownBy(() -> shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
			fixture.campus().id(),
			group.groupId(),
			sunday,
			fixture.memberA().id(),
			4,
			4,
			1,
			"stale",
			"SUBMITTED",
			0
		)))
			.isInstanceOf(BusinessException.class)
			.extracting("errorCode")
			.isEqualTo(ErrorCode.SHEPHERD_ATTENDANCE_CONFLICT);
	}

	@Test
	void rejects_non_sunday_and_negative_counts() {
		Fixture fixture = createFixture("invalid-counts");
		ShepherdGroupResult group = shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.memberA().id(),
			"온유 목장",
			List.of()
		));

		assertThatThrownBy(() -> shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
			fixture.campus().id(), group.groupId(), LocalDate.of(2026, 8, 17), fixture.memberA().id(),
			1, 0, 0, null, "DRAFT", 0
		))).extracting("errorCode").isEqualTo(ErrorCode.SHEPHERD_INVALID_SERVICE_DATE);
		assertThatThrownBy(() -> shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
			fixture.campus().id(), group.groupId(), LocalDate.of(2026, 8, 16), fixture.memberA().id(),
			-1, 0, 0, null, "DRAFT", 0
		))).extracting("errorCode").isEqualTo(ErrorCode.SHEPHERD_INVALID_ATTENDANCE_COUNT);
	}

	@Test
	void regular_member_cannot_read_or_write_unassigned_group_but_manager_can() {
		Fixture fixture = createFixture("permission");
		ShepherdGroupResult group = shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.memberA().id(),
			"화평 목장",
			List.of()
		));
		LocalDate sunday = LocalDate.of(2026, 8, 16);

		assertThatThrownBy(() -> shepherdService.getAttendance(
			fixture.campus().id(), group.groupId(), sunday, fixture.memberB().id()
		)).extracting("errorCode").isEqualTo(ErrorCode.SHEPHERD_GROUP_NOT_FOUND);
		assertThatThrownBy(() -> shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
			fixture.campus().id(), group.groupId(), sunday, fixture.memberB().id(), 1, 1, 1, null, "DRAFT", 0
		))).extracting("errorCode").isEqualTo(ErrorCode.SHEPHERD_GROUP_NOT_FOUND);

		ShepherdAttendanceReportResult managerWrite = shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
			fixture.campus().id(), group.groupId(), sunday, fixture.manager().id(), 5, 2, 1, "관리자 입력", "SUBMITTED", 0
		));
		assertThat(managerWrite.status()).isEqualTo("SUBMITTED");
	}

	@Test
	void get_attendance_returns_last_modifier_name_after_entity_cache_is_cleared() {
		Fixture fixture = createFixture("modifier-name");
		ShepherdGroupResult group = shepherdService.createGroup(new CreateShepherdGroupCommand(
			fixture.campus().id(),
			fixture.memberA().id(),
			"수정자 목장",
			List.of()
		));
		LocalDate sunday = LocalDate.of(2026, 8, 16);
		shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
			fixture.campus().id(),
			group.groupId(),
			sunday,
			fixture.memberA().id(),
			1,
			1,
			1,
			null,
			"SUBMITTED",
			0
		));
		entityManager.flush();
		entityManager.clear();

		ShepherdAttendanceReportResult reread = shepherdService.getAttendance(
			fixture.campus().id(), group.groupId(), sunday, fixture.memberA().id());

		assertThat(reread.lastModifiedByUserId()).isEqualTo(fixture.memberA().id());
		assertThat(reread.lastModifiedByName()).isEqualTo(fixture.memberA().name());
	}

	@Test
	void manager_create_does_not_issue_merge_existence_select_for_each_new_assignee() {
		Fixture one = createFixture("assignee-select-one");
		List<Long> oneAssignee = createAdditionalActiveMembers(one, 1);
		ShepherdSqlCounterStatementInspector.reset();

		shepherdService.createGroup(new CreateShepherdGroupCommand(
			one.campus().id(), one.manager().id(), "담당자 1", oneAssignee));

		assertThat(ShepherdSqlCounterStatementInspector.assigneeSelectCount()).isEqualTo(1);

		Fixture hundred = createFixture("assignee-select-hundred");
		List<Long> hundredAssignees = createAdditionalActiveMembers(hundred, 100);
		ShepherdSqlCounterStatementInspector.reset();

		shepherdService.createGroup(new CreateShepherdGroupCommand(
			hundred.campus().id(), hundred.manager().id(), "담당자 100", hundredAssignees));

		assertThat(ShepherdSqlCounterStatementInspector.assigneeSelectCount()).isEqualTo(1);
	}

	@Test
	void admin_weekly_board_returns_missing_rows_totals_and_constant_query_count_for_large_pages() {
		Fixture fixture = createFixture("board");
		LocalDate sunday = LocalDate.of(2026, 8, 16);
		List<Long> groupIds = new ArrayList<>();
		for (int index = 0; index < 100; index++) {
			ShepherdGroupResult group = shepherdService.createGroup(new CreateShepherdGroupCommand(
				fixture.campus().id(),
				fixture.manager().id(),
				"목장 " + index,
				List.of(fixture.memberA().id())
			));
			groupIds.add(group.groupId());
			if (index < 40) {
				shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
					fixture.campus().id(), group.groupId(), sunday, fixture.manager().id(),
					1, 2, 3, null, "SUBMITTED", 0
				));
			}
		}
		Statistics statistics = resetStatistics();

		ShepherdAttendanceBoardResult board = shepherdService.getAdminAttendanceBoard(
			fixture.campus().id(),
			sunday,
			fixture.manager().id(),
			PageRequest.of(0, 100)
		);

		assertThat(board.serviceDate()).isEqualTo(sunday);
		assertThat(board.totalSubmittedCount()).isEqualTo(40);
		assertThat(board.totalMissingCount()).isEqualTo(60);
		assertThat(board.totalSmallGroupMeetingCount()).isEqualTo(40);
		assertThat(board.totalHolyWaveCount()).isEqualTo(80);
		assertThat(board.totalOtherWorshipCount()).isEqualTo(120);
		assertThat(board.groups()).hasSize(100);
		assertThat(board.groups()).extracting(ShepherdAttendanceBoardGroupResult::groupName)
			.containsExactly(
				"목장 0", "목장 1", "목장 10", "목장 11", "목장 12", "목장 13", "목장 14", "목장 15", "목장 16", "목장 17",
				"목장 18", "목장 19", "목장 2", "목장 20", "목장 21", "목장 22", "목장 23", "목장 24", "목장 25", "목장 26",
				"목장 27", "목장 28", "목장 29", "목장 3", "목장 30", "목장 31", "목장 32", "목장 33", "목장 34", "목장 35",
				"목장 36", "목장 37", "목장 38", "목장 39", "목장 4", "목장 40", "목장 41", "목장 42", "목장 43", "목장 44",
				"목장 45", "목장 46", "목장 47", "목장 48", "목장 49", "목장 5", "목장 50", "목장 51", "목장 52", "목장 53",
				"목장 54", "목장 55", "목장 56", "목장 57", "목장 58", "목장 59", "목장 6", "목장 60", "목장 61", "목장 62",
				"목장 63", "목장 64", "목장 65", "목장 66", "목장 67", "목장 68", "목장 69", "목장 7", "목장 70", "목장 71",
				"목장 72", "목장 73", "목장 74", "목장 75", "목장 76", "목장 77", "목장 78", "목장 79", "목장 8", "목장 80",
				"목장 81", "목장 82", "목장 83", "목장 84", "목장 85", "목장 86", "목장 87", "목장 88", "목장 89", "목장 9",
				"목장 90", "목장 91", "목장 92", "목장 93", "목장 94", "목장 95", "목장 96", "목장 97", "목장 98", "목장 99"
			);
		assertThat(board.groups()).filteredOn(group -> group.report() == null).hasSize(60);
		assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
	}

	@Test
	void admin_weekly_board_counts_only_submitted_reports_as_completed() {
		Fixture fixture = createFixture("board-draft");
		LocalDate sunday = LocalDate.of(2026, 8, 16);
		ShepherdGroup submitted = seedGroup(fixture, "완료 목장", fixture.memberA().id());
		ShepherdGroup draft = seedGroup(fixture, "임시 목장", fixture.memberA().id());
		shepherdAttendanceReportRepository.save(WeeklyShepherdAttendanceReport.create(
			fixture.campus().id(),
			submitted.id(),
			sunday,
			1,
			1,
			1,
			null,
			WeeklyShepherdAttendanceStatus.SUBMITTED,
			fixture.manager().id(),
			java.time.Instant.now()
		));
		shepherdAttendanceReportRepository.save(WeeklyShepherdAttendanceReport.create(
			fixture.campus().id(),
			draft.id(),
			sunday,
			2,
			2,
			2,
			null,
			WeeklyShepherdAttendanceStatus.DRAFT,
			fixture.manager().id(),
			java.time.Instant.now()
		));

		ShepherdAttendanceBoardResult board = shepherdService.getAdminAttendanceBoard(
			fixture.campus().id(),
			sunday,
			fixture.manager().id(),
			PageRequest.of(0, 100)
		);

		assertThat(board.totalSubmittedCount()).isEqualTo(1);
		assertThat(board.totalMissingCount()).isEqualTo(1);
	}

	@Test
	void admin_weekly_board_query_count_is_constant_for_one_hundred_and_thousand_groups() {
		LocalDate sunday = LocalDate.of(2026, 8, 16);
		List<Long> counts = new ArrayList<>();
		for (int groupCount : List.of(1, 100, 1000)) {
			Fixture fixture = createFixture("constant-query-" + groupCount);
			seedAttendanceBoardFixture(fixture, sunday, groupCount);
			Statistics statistics = resetStatistics();

			ShepherdAttendanceBoardResult board = shepherdService.getAdminAttendanceBoard(
				fixture.campus().id(),
				sunday,
				fixture.manager().id(),
				PageRequest.of(0, 100)
			);

			assertThat(board.totalElements()).isEqualTo(groupCount);
			assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
			counts.add(statistics.getPrepareStatementCount());
		}
		assertThat(counts).containsExactly(4L, 4L, 4L);
	}

	@Test
	void home_card_visible_only_on_seoul_sunday_for_assigned_groups_and_submitted_count() {
		setSeoulNow("2026-08-16T00:00:00+09:00");
		Fixture fixture = createFixture("home-sunday");
		LocalDate sunday = LocalDate.of(2026, 8, 16);
		ShepherdGroup first = seedGroup(fixture, "홈 1", fixture.memberA().id());
		ShepherdGroup second = seedGroup(fixture, "홈 2", fixture.memberA().id());
		shepherdAttendanceReportRepository.save(WeeklyShepherdAttendanceReport.create(
			fixture.campus().id(),
			second.id(),
			sunday,
			2,
			3,
			4,
			"완료",
			WeeklyShepherdAttendanceStatus.SUBMITTED,
			fixture.memberA().id(),
			Instant.now()
		));

		ShepherdHomeCardResult home = shepherdService.getMyHome(
			fixture.campus().id(), fixture.memberA().id());

		assertThat(home.visible()).isTrue();
		assertThat(home.title()).isEqualTo("이번 주 목홀타를 입력해 주세요");
		assertThat(home.serviceDate()).isEqualTo(sunday);
		assertThat(home.assignedGroupCount()).isEqualTo(2);
		assertThat(home.submittedGroupCount()).isEqualTo(1);
		assertThat(home.groups()).hasSize(2);
		assertThat(home.groups()).extracting("groupId").containsExactly(first.id(), second.id());
		assertThat(home.groups().get(0).report()).isNull();
		assertThat(home.groups().get(1).report().status()).isEqualTo("SUBMITTED");
	}

	@Test
	void home_card_hides_outside_seoul_sunday_and_does_not_return_group_details() {
		setSeoulNow("2026-08-17T00:00:00+09:00");
		Fixture fixture = createFixture("home-monday");
		seedGroup(fixture, "월요일 숨김", fixture.memberA().id());

		ShepherdHomeCardResult home = shepherdService.getMyHome(
			fixture.campus().id(), fixture.memberA().id());

		assertThat(home.visible()).isFalse();
		assertThat(home.serviceDate()).isNull();
		assertThat(home.assignedGroupCount()).isZero();
		assertThat(home.submittedGroupCount()).isZero();
		assertThat(home.groups()).isEmpty();
	}

	@Test
	void home_card_hidden_for_unassigned_member_and_unassigned_manager() {
		setSeoulNow("2026-08-16T12:00:00+09:00");
		Fixture fixture = createFixture("home-unassigned");
		seedGroup(fixture, "다른 담당", fixture.memberA().id());

		ShepherdHomeCardResult memberHome = shepherdService.getMyHome(
			fixture.campus().id(), fixture.memberB().id());
		ShepherdHomeCardResult managerHome = shepherdService.getMyHome(
			fixture.campus().id(), fixture.manager().id());

		assertThat(memberHome.visible()).isFalse();
		assertThat(memberHome.groups()).isEmpty();
		assertThat(managerHome.visible()).isFalse();
		assertThat(managerHome.groups()).isEmpty();
	}

	@Test
	void home_card_query_count_is_constant_for_one_and_hundred_assigned_groups() {
		setSeoulNow("2026-08-16T23:59:59+09:00");
		List<Long> counts = new ArrayList<>();
		for (int groupCount : List.of(1, 100)) {
			Fixture fixture = createFixture("home-query-" + groupCount);
			for (int index = 0; index < groupCount; index++) {
				seedGroup(fixture, "홈상수 " + groupCount + " " + String.format("%03d", index), fixture.memberA().id());
			}
			Statistics statistics = resetStatistics();

			ShepherdHomeCardResult home = shepherdService.getMyHome(
				fixture.campus().id(), fixture.memberA().id());

			assertThat(home.visible()).isTrue();
			assertThat(home.assignedGroupCount()).isEqualTo(groupCount);
			assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
			counts.add(statistics.getPrepareStatementCount());
		}
		assertThat(counts).containsExactly(2L, 2L);
	}

	private Statistics resetStatistics() {
		entityManager.flush();
		entityManager.clear();
		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		return statistics;
	}

	private Fixture createFixture(String suffix) {
		User manager = saveUser("shepherd-" + suffix + "-manager@example.com", UserRole.MANAGER);
		User memberA = saveUser("shepherd-" + suffix + "-a@example.com", UserRole.USER);
		User memberB = saveUser("shepherd-" + suffix + "-b@example.com", UserRole.USER);
		User otherCampusMember = saveUser("shepherd-" + suffix + "-other@example.com", UserRole.USER);
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"Shepherd " + suffix,
			"Seoul",
			"목장 테스트",
			"SHEPHERD-" + suffix
		));
		Campus otherCampus = campusRepository.saveAndFlush(Campus.create(
			"Other " + suffix,
			"Busan",
			"다른 캠퍼스",
			"SHEPHERD-OTHER-" + suffix
		));
		CampusMember managerMembership = CampusMember.createMinister(campus.id(), manager.id());
		managerMembership.changeCampusRole(CampusRole.MINISTER);
		campusMemberRepository.saveAndFlush(managerMembership);
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), memberA.id()));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), memberB.id()));
		campusMemberRepository.saveAndFlush(CampusMember.createMember(otherCampus.id(), otherCampusMember.id()));
		return new Fixture(campus, manager, memberA, memberB, otherCampusMember);
	}

	private void seedAttendanceBoardFixture(Fixture fixture, LocalDate sunday, int groupCount) {
		for (int index = 0; index < groupCount; index++) {
			String name = "상수쿼리 " + groupCount + " " + String.format("%04d", index);
			ShepherdGroup group = seedGroup(fixture, name, fixture.memberA().id());
			if (index % 2 == 0) {
				shepherdAttendanceReportRepository.save(WeeklyShepherdAttendanceReport.create(
					fixture.campus().id(),
					group.id(),
					sunday,
					1,
					1,
					1,
					null,
					WeeklyShepherdAttendanceStatus.SUBMITTED,
					fixture.manager().id(),
					java.time.Instant.now()
				));
			}
		}
	}

	private ShepherdGroup seedGroup(Fixture fixture, String name, Long assigneeUserId) {
		ShepherdGroup group = shepherdGroupRepository.save(ShepherdGroup.create(
			fixture.campus().id(),
			name,
			name.toLowerCase(java.util.Locale.ROOT),
			fixture.manager().id()
		));
		shepherdGroupAssigneeRepository.save(ShepherdGroupAssignee.create(
			group.id(),
			fixture.campus().id(),
			assigneeUserId,
			fixture.manager().id()
		));
		return group;
	}

	private List<Long> createAdditionalActiveMembers(Fixture fixture, int count) {
		List<Long> userIds = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			User user = saveUser("shepherd-extra-" + fixture.campus().id() + "-" + index + "@example.com", UserRole.USER);
			campusMemberRepository.saveAndFlush(CampusMember.createMember(fixture.campus().id(), user.id()));
			userIds.add(user.id());
		}
		return userIds;
	}

	private void setSeoulNow(String seoulDateTime) {
		TEST_CLOCK.set(Instant.from(java.time.OffsetDateTime.parse(seoulDateTime).atZoneSameInstant(ZoneOffset.UTC)));
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("Shepherd User", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private record Fixture(Campus campus, User manager, User memberA, User memberB, User otherCampusMember) {
	}

	@TestConfiguration
	static class ClockTestConfig {

		@Bean
		@Primary
		Clock shepherdTestClock() {
			return TEST_CLOCK;
		}
	}

	private static class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return SEOUL_ZONE;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return Clock.fixed(instant, zone);
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
