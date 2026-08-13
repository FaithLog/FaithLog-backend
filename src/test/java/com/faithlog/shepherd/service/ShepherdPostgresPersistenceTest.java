package com.faithlog.shepherd.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.shepherd.domain.entity.ShepherdGroup;
import com.faithlog.shepherd.infrastructure.repository.ShepherdGroupAssigneeRepository;
import com.faithlog.shepherd.infrastructure.repository.ShepherdGroupRepository;
import com.faithlog.shepherd.infrastructure.repository.WeeklyShepherdAttendanceReportRepository;
import com.faithlog.shepherd.service.command.CreateShepherdGroupCommand;
import com.faithlog.shepherd.service.command.SaveShepherdAttendanceCommand;
import com.faithlog.shepherd.service.command.UpdateShepherdGroupCommand;
import com.faithlog.shepherd.service.result.ShepherdAttendanceReportResult;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "FAITHLOG_RUN_POSTGRES_SHEPHERD_TEST", matches = "true")
class ShepherdPostgresPersistenceTest {

	private static final LocalDate SUNDAY = LocalDate.of(2026, 8, 16);

	@Autowired private ShepherdService shepherdService;
	@MockitoSpyBean private ShepherdGroupRepository shepherdGroupRepository;
	@Autowired private ShepherdGroupAssigneeRepository shepherdGroupAssigneeRepository;
	@Autowired private WeeklyShepherdAttendanceReportRepository reportRepository;
	@Autowired private CampusMemberRepository campusMemberRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PlatformTransactionManager transactionManager;

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", () -> envOrDefault("SHEPHERD_PG_JDBC_URL", "jdbc:postgresql://localhost:5432/faithlog_test"));
		registry.add("spring.datasource.username", () -> envOrDefault("SHEPHERD_PG_USERNAME", "faithlog"));
		registry.add("spring.datasource.password", () -> envOrDefault("SHEPHERD_PG_PASSWORD", "faithlog"));
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
		registry.add("spring.flyway.enabled", () -> "false");
	}

	@AfterEach
	void clean() {
		transaction().executeWithoutResult(status -> {
			reportRepository.deleteAll();
			shepherdGroupAssigneeRepository.deleteAll();
			shepherdGroupRepository.deleteAll();
			campusMemberRepository.deleteAll();
			campusRepository.deleteAll();
			userRepository.deleteAll();
		});
	}

	@Test
	@Timeout(10)
	void postgres_concurrent_normalized_create_has_one_group_and_one_assignee_set() throws Exception {
		Fixture fixture = persistFixture("pg-create");
		CountDownLatch bothCheckedDuplicate = new CountDownLatch(2);
		doAnswer(invocation -> {
			bothCheckedDuplicate.countDown();
			assertThat(bothCheckedDuplicate.await(5, TimeUnit.SECONDS)).isTrue();
			return false;
		}).when(shepherdGroupRepository)
			.existsByCampusIdAndNormalizedName(eq(fixture.campus().id()), eq("공백 목장"));
		var executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			var left = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return createGroup(fixture, "  공백   목장  ");
			});
			var right = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return createGroup(fixture, "공백 목장");
			});
			start.countDown();

			List<ErrorCode> outcomes = Arrays.asList(left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS));
			List<ShepherdGroup> groups = shepherdGroupRepository.findAll().stream()
				.filter(group -> group.campusId().equals(fixture.campus().id()))
				.filter(group -> group.normalizedName().equals("공백 목장"))
				.toList();

			assertThat(outcomes).contains(null, ErrorCode.SHEPHERD_GROUP_DUPLICATE);
			assertThat(groups).hasSize(1);
			assertThat(shepherdGroupAssigneeRepository
				.findByCampusIdAndShepherdGroupIdOrderByUserIdAsc(fixture.campus().id(), groups.getFirst().id()))
				.extracting("userId")
				.containsExactly(fixture.manager().id());
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@Timeout(10)
	void postgres_concurrent_normalized_rename_returns_typed_duplicate_409() throws Exception {
		Fixture fixture = persistFixture("pg-rename");
		ShepherdGroup first = saveGroup(fixture, "포스트 첫 목장");
		ShepherdGroup second = saveGroup(fixture, "포스트 둘째 목장");
		var executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			var left = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return rename(fixture, first.id(), "포스트 충돌");
			});
			var right = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return rename(fixture, second.id(), "포스트 충돌");
			});
			start.countDown();

			List<ErrorCode> outcomes = Arrays.asList(left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS));

			assertThat(outcomes).contains(null, ErrorCode.SHEPHERD_GROUP_DUPLICATE);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	@Timeout(10)
	void postgres_concurrent_first_attendance_save_has_one_winner() throws Exception {
		Fixture fixture = persistFixture("pg-first-report");
		ShepherdGroup group = saveGroup(fixture, "보고 목장");
		var executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			var left = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return saveAttendance(fixture, group.id(), 1, 0);
			});
			var right = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return saveAttendance(fixture, group.id(), 2, 0);
			});
			start.countDown();

			List<ErrorCode> outcomes = Arrays.asList(left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS));

			assertThat(outcomes).contains(null, ErrorCode.SHEPHERD_ATTENDANCE_CONFLICT);
			assertThat(reportRepository.findAll()).hasSize(1);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void postgres_stale_attendance_version_returns_409() {
		Fixture fixture = persistFixture("pg-stale");
		ShepherdGroup group = saveGroup(fixture, "stale 목장");
		ShepherdAttendanceReportResult saved = shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
			fixture.campus().id(), group.id(), SUNDAY, fixture.manager().id(),
			1, 1, 1, null, "DRAFT", 0));

		assertThat(saved.version()).isEqualTo(1);
		assertThat(saveAttendance(fixture, group.id(), 2, 0))
			.isEqualTo(ErrorCode.SHEPHERD_ATTENDANCE_CONFLICT);
	}

	private ErrorCode rename(Fixture fixture, Long groupId, String name) {
		try {
			shepherdService.updateGroup(new UpdateShepherdGroupCommand(
				fixture.campus().id(), groupId, fixture.manager().id(), name, 1));
			return null;
		} catch (BusinessException exception) {
			return exception.errorCode();
		}
	}

	private ErrorCode createGroup(Fixture fixture, String name) {
		try {
			shepherdService.createGroup(new CreateShepherdGroupCommand(
				fixture.campus().id(), fixture.manager().id(), name, List.of(fixture.manager().id())));
			return null;
		} catch (BusinessException exception) {
			return exception.errorCode();
		}
	}

	private ErrorCode saveAttendance(Fixture fixture, Long groupId, int count, int version) {
		try {
			shepherdService.saveAttendance(new SaveShepherdAttendanceCommand(
				fixture.campus().id(), groupId, SUNDAY, fixture.manager().id(),
				count, 0, 0, null, "SUBMITTED", version));
			return null;
		} catch (BusinessException exception) {
			return exception.errorCode();
		}
	}

	private Fixture persistFixture(String suffix) {
		return transaction().execute(status -> {
			User manager = User.create("PG목장관리자", suffix + "-manager@example.com", "encoded");
			ReflectionTestUtils.setField(manager, "role", UserRole.MANAGER);
			manager = userRepository.saveAndFlush(manager);
			Campus campus = campusRepository.saveAndFlush(Campus.create(
				"PG 목장 " + suffix,
				"서울",
				"PostgreSQL",
				"PG-SHEPHERD-" + suffix
			));
			CampusMember membership = CampusMember.createMinister(campus.id(), manager.id());
			membership.changeCampusRole(CampusRole.MINISTER);
			campusMemberRepository.saveAndFlush(membership);
			return new Fixture(campus, manager);
		});
	}

	private ShepherdGroup saveGroup(Fixture fixture, String name) {
		return transaction().execute(status -> shepherdGroupRepository.saveAndFlush(ShepherdGroup.create(
			fixture.campus().id(),
			name,
			name.toLowerCase(java.util.Locale.ROOT),
			fixture.manager().id()
		)));
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private static String envOrDefault(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isBlank() ? fallback : value;
	}

	private record Fixture(Campus campus, User manager) {
	}
}
