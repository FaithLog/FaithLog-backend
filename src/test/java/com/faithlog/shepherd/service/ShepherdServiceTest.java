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
import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardGroupResult;
import com.faithlog.shepherd.service.result.ShepherdAttendanceBoardResult;
import com.faithlog.shepherd.service.result.ShepherdAttendanceReportResult;
import com.faithlog.shepherd.service.result.ShepherdGroupResult;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class ShepherdServiceTest {

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
			ShepherdGroup group = shepherdGroupRepository.save(ShepherdGroup.create(
				fixture.campus().id(),
				name,
				name.toLowerCase(java.util.Locale.ROOT),
				fixture.manager().id()
			));
			shepherdGroupAssigneeRepository.save(ShepherdGroupAssignee.create(
				group.id(),
				fixture.campus().id(),
				fixture.memberA().id(),
				fixture.manager().id()
			));
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

	private User saveUser(String email, UserRole role) {
		User user = User.create("Shepherd User", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private record Fixture(Campus campus, User manager, User memberA, User memberB, User otherCampusMember) {
	}
}
