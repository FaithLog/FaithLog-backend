package com.faithlog.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.faithlog.admin.service.command.ChangeUserRoleCommand;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AdminUserRoleConcurrencyTest {

	@Autowired
	private AdminManagementService adminManagementService;

	@MockitoSpyBean
	private UserRepository userRepository;

	@Test
	void concurrent_self_demotion_preserves_exactly_one_active_service_admin() throws Exception {
		User first = saveAdmin("last-admin-first@example.com");
		User second = saveAdmin("last-admin-second@example.com");
		assertThat(userRepository.countByRoleAndIsActiveTrue(UserRole.ADMIN)).isEqualTo(2);

		CountDownLatch bothCounted = new CountDownLatch(2);
		CountDownLatch allowDemotion = new CountDownLatch(1);
		doAnswer(invocation -> {
			long count = (long) invocation.callRealMethod();
			if (Thread.currentThread().getName().startsWith("admin-demotion")) {
				bothCounted.countDown();
				if (!bothCounted.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("both admin demotions did not reach the count boundary");
				}
				if (!allowDemotion.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("admin demotion release timeout");
				}
			}
			return count;
		}).when(userRepository).countByRoleAndIsActiveTrue(UserRole.ADMIN);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> firstDemotion = executor.submit(() -> {
				Thread.currentThread().setName("admin-demotion-first");
				adminManagementService.changeUserRole(new ChangeUserRoleCommand(
					first.id(), first.id(), UserRole.USER));
			});
			Future<?> secondDemotion = executor.submit(() -> {
				Thread.currentThread().setName("admin-demotion-second");
				adminManagementService.changeUserRole(new ChangeUserRoleCommand(
					second.id(), second.id(), UserRole.USER));
			});

			assertThat(bothCounted.await(5, TimeUnit.SECONDS)).isTrue();
			allowDemotion.countDown();
			List<Future<?>> attempts = List.of(firstDemotion, secondDemotion);
			int successes = 0;
			int lastAdminConflicts = 0;
			for (Future<?> attempt : attempts) {
				try {
					attempt.get(5, TimeUnit.SECONDS);
					successes++;
				} catch (java.util.concurrent.ExecutionException exception) {
					if (exception.getCause() instanceof BusinessException businessException
						&& businessException.errorCode() == ErrorCode.ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN) {
						lastAdminConflicts++;
					} else {
						throw exception;
					}
				}
			}

			assertThat(successes).isEqualTo(1);
			assertThat(lastAdminConflicts).isEqualTo(1);
			assertThat(userRepository.countByRoleAndIsActiveTrue(UserRole.ADMIN)).isEqualTo(1);
		} finally {
			allowDemotion.countDown();
			executor.shutdownNow();
		}
	}

	private User saveAdmin(String email) {
		User user = User.create("서비스관리자", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", UserRole.ADMIN);
		return userRepository.saveAndFlush(user);
	}
}
