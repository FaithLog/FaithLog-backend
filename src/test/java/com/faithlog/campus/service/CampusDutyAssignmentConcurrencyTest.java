package com.faithlog.campus.service;

import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.command.AssignMealDutyCommand;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CampusDutyAssignmentConcurrencyTest {

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Test
	void assignCoffeeDuty_keeps_multiple_members_and_one_active_assignment_per_member_under_concurrency() throws Exception {
		User manager = saveUser("coffee-concurrent-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"동시커피캠",
			"분당",
			"동시 커피 담당자 테스트 캠퍼스"
		));
		List<User> targets = new ArrayList<>();
		for (int index = 0; index < 12; index++) {
			User target = saveUser("coffee-concurrent-target-%02d@example.com".formatted(index), UserRole.USER);
			campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
			targets.add(target);
		}

		ExecutorService executor = Executors.newFixedThreadPool(targets.size());
		CountDownLatch ready = new CountDownLatch(targets.size());
		CountDownLatch start = new CountDownLatch(1);
		List<User> requests = new ArrayList<>(targets);
		requests.add(targets.getFirst());
		requests.add(targets.getFirst());
		List<Future<DutyAssignmentResult>> futures = requests.stream()
			.map(target -> executor.submit(() -> {
				ready.countDown();
				start.await(5, TimeUnit.SECONDS);
				return campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
					campus.campusId(),
					manager.id(),
					target.id()
				));
			}))
			.toList();

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		for (Future<DutyAssignmentResult> future : futures) {
			future.get(5, TimeUnit.SECONDS);
		}
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		List<DutyAssignmentResult> activeAssignments = campusService.getDutyAssignments(campus.campusId(), manager.id());
		assertThat(activeAssignments)
			.hasSize(targets.size())
			.allMatch(DutyAssignmentResult::active)
			.extracting(DutyAssignmentResult::userId)
			.containsExactlyInAnyOrderElementsOf(targets.stream().map(User::id).toList());
	}

	@Test
	void assignMealDuty_keeps_multiple_members_but_one_active_assignment_per_member_under_concurrency() throws Exception {
		User manager = saveUser("meal-concurrent-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "동시밥캠", "분당", "동시 밥 담당자 테스트 캠퍼스"
		));
		List<User> targets = new ArrayList<>();
		for (int index = 0; index < 6; index++) {
			User target = saveUser("meal-concurrent-target-%02d@example.com".formatted(index), UserRole.USER);
			campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
			targets.add(target);
		}
		List<User> requests = new ArrayList<>(targets);
		requests.add(targets.getFirst());
		requests.add(targets.getFirst());
		ExecutorService executor = Executors.newFixedThreadPool(requests.size());
		CountDownLatch ready = new CountDownLatch(requests.size());
		CountDownLatch start = new CountDownLatch(1);
		List<Future<DutyAssignmentResult>> futures = requests.stream().map(target -> executor.submit(() -> {
			ready.countDown();
			start.await(5, TimeUnit.SECONDS);
			return campusService.assignMealDuty(new AssignMealDutyCommand(
				campus.campusId(), manager.id(), target.id()
			));
		})).toList();

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		for (Future<DutyAssignmentResult> future : futures) {
			future.get(5, TimeUnit.SECONDS);
		}
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
		assertThat(campusService.getDutyAssignments(campus.campusId(), manager.id()).stream()
			.filter(assignment -> assignment.dutyType().equals("MEAL")))
			.hasSize(6)
			.extracting(DutyAssignmentResult::userId)
			.containsExactlyInAnyOrderElementsOf(targets.stream().map(User::id).toList());
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("동시성테스트", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
