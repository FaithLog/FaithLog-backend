package com.faithlog.campus.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
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
	void assignCoffeeDuty_keeps_only_one_active_coffee_assignment_under_concurrent_requests() throws Exception {
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
		List<Future<DutyAssignmentResult>> futures = targets.stream()
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
		assertThat(activeAssignments).hasSize(1);
		assertThat(activeAssignments.getFirst().active()).isTrue();
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("동시성테스트", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
