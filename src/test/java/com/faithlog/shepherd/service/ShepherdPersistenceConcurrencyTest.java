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
import com.faithlog.shepherd.service.command.UpdateShepherdGroupCommand;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class ShepherdPersistenceConcurrencyTest {

	@Autowired private ShepherdService shepherdService;
	@MockitoSpyBean private ShepherdGroupRepository shepherdGroupRepository;
	@Autowired private ShepherdGroupAssigneeRepository shepherdGroupAssigneeRepository;
	@Autowired private WeeklyShepherdAttendanceReportRepository reportRepository;
	@Autowired private CampusMemberRepository campusMemberRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PlatformTransactionManager transactionManager;

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
	void concurrent_group_rename_unique_collision_returns_typed_duplicate_409() throws Exception {
		Fixture fixture = persistFixture("rename-race");
		ShepherdGroup first = saveGroup(fixture, "첫 목장");
		ShepherdGroup second = saveGroup(fixture, "둘째 목장");
		CountDownLatch bothCheckedDuplicate = new CountDownLatch(2);
		CountDownLatch allowRename = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			bothCheckedDuplicate.countDown();
			assertThat(bothCheckedDuplicate.await(5, TimeUnit.SECONDS)).isTrue();
			allowRename.countDown();
			return result;
		}).when(shepherdGroupRepository)
			.existsByCampusIdAndNormalizedName(eq(fixture.campus().id()), eq("충돌 목장"));
		var executor = Executors.newFixedThreadPool(2);
		try {
			var left = executor.submit(() -> rename(fixture, first.id(), "충돌 목장"));
			var right = executor.submit(() -> rename(fixture, second.id(), "충돌 목장"));
			assertThat(allowRename.await(5, TimeUnit.SECONDS)).isTrue();

			List<ErrorCode> outcomes = Arrays.asList(left.get(5, TimeUnit.SECONDS), right.get(5, TimeUnit.SECONDS));

			assertThat(outcomes).contains(null, ErrorCode.SHEPHERD_GROUP_DUPLICATE);
		} finally {
			allowRename.countDown();
			executor.shutdownNow();
		}
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

	private Fixture persistFixture(String suffix) {
		return transaction().execute(status -> {
			User manager = User.create("목장관리자", suffix + "-manager@example.com", "encoded");
			ReflectionTestUtils.setField(manager, "role", UserRole.MANAGER);
			manager = userRepository.saveAndFlush(manager);
			Campus campus = campusRepository.saveAndFlush(Campus.create(
				"목장 동시성 " + suffix,
				"서울",
				"동시성",
				"SHEPHERD-CONCURRENCY-" + suffix
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

	private record Fixture(Campus campus, User manager) {
	}
}
