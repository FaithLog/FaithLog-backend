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
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

	@Autowired
	private EntityManager entityManager;

	@Test
	void concurrent_self_demotion_preserves_exactly_one_active_service_admin() throws Exception {
		User first = saveAdmin("last-admin-first@example.com");
		User second = saveAdmin("last-admin-second@example.com");
		assertThat(userRepository.countByRoleAndIsActiveTrue(UserRole.ADMIN)).isEqualTo(2);

		CountDownLatch start = new CountDownLatch(1);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> firstDemotion = executor.submit(() -> {
				Thread.currentThread().setName("admin-demotion-first");
				await(start);
				adminManagementService.changeUserRole(new ChangeUserRoleCommand(
					first.id(), first.id(), UserRole.USER));
			});
			Future<?> secondDemotion = executor.submit(() -> {
				Thread.currentThread().setName("admin-demotion-second");
				await(start);
				adminManagementService.changeUserRole(new ChangeUserRoleCommand(
					second.id(), second.id(), UserRole.USER));
			});

			start.countDown();
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
			start.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void role_change_rechecks_latest_target_role_before_last_admin_demotion() throws Exception {
		User firstAdmin = saveAdmin("role-transition-first-admin@example.com");
		User target = User.create("승격대상", "role-transition-target@example.com", "dummy-password-hash");
		target = userRepository.saveAndFlush(target);
		Long targetId = target.id();
		CountDownLatch targetScopeRead = new CountDownLatch(1);
		CountDownLatch allowTargetLock = new CountDownLatch(1);
		AtomicBoolean paused = new AtomicBoolean();
		doAnswer(invocation -> {
			if (Thread.currentThread().getName().equals("stale-role-demotion")
				&& paused.compareAndSet(false, true)) {
				targetScopeRead.countDown();
				if (!allowTargetLock.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("target role lock release timeout");
				}
			}
			return entityManager.createQuery("select user from User user order by user.id asc", User.class)
				.setMaxResults(1)
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.getResultStream()
				.findFirst();
		}).when(userRepository).findFirstAdminMutationLockForUpdate();

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> staleDemotion = executor.submit(() -> {
				Thread.currentThread().setName("stale-role-demotion");
				adminManagementService.changeUserRole(new ChangeUserRoleCommand(
					targetId, targetId, UserRole.USER));
			});
			assertThat(targetScopeRead.await(5, TimeUnit.SECONDS)).isTrue();
			adminManagementService.changeUserRole(new ChangeUserRoleCommand(
				firstAdmin.id(), targetId, UserRole.ADMIN));
			adminManagementService.changeUserRole(new ChangeUserRoleCommand(
				firstAdmin.id(), firstAdmin.id(), UserRole.USER));
			allowTargetLock.countDown();

			org.assertj.core.api.Assertions.assertThatThrownBy(
				() -> staleDemotion.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.ADMIN_LAST_ADMIN_DEMOTION_FORBIDDEN));
			assertThat(userRepository.findById(targetId))
				.get().satisfies(user -> {
					assertThat(user.role()).isEqualTo(UserRole.ADMIN);
					assertThat(user.isActive()).isTrue();
				});
			assertThat(userRepository.countByRoleAndIsActiveTrue(UserRole.ADMIN)).isEqualTo(1);
		} finally {
			allowTargetLock.countDown();
			executor.shutdownNow();
		}
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("admin demotion start timeout");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private User saveAdmin(String email) {
		User user = User.create("서비스관리자", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", UserRole.ADMIN);
		return userRepository.saveAndFlush(user);
	}
}
