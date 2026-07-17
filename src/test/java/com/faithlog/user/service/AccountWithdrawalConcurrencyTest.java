package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.command.DeleteMyAccountCommand;
import com.faithlog.user.service.command.LoginCommand;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountWithdrawalConcurrencyTest {

	@Autowired
	private AccountWithdrawalCommandService withdrawalService;

	@Autowired
	private LoginCommandService loginService;

	@Autowired
	private CampusService campusService;

	@MockitoSpyBean
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@MockitoSpyBean
	private AuthTokenIssuanceSupport tokenIssuanceSupport;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private EntityManager entityManager;

	@Test
	void withdrawal_blocks_new_campus_join_after_membership_scope_snapshot() throws Exception {
		User manager = saveUser("withdrawal-join-manager@example.com", UserRole.MANAGER);
		User deletingUser = saveUser("withdrawal-join-user@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "탈퇴중신규가입캠", "분당", "탈퇴와 신규 가입 직렬화 RED"
		));
		CountDownLatch userLocked = new CountDownLatch(1);
		CountDownLatch allowWithdrawal = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = java.util.Optional.ofNullable(entityManager.find(
				User.class,
				deletingUser.id(),
				LockModeType.PESSIMISTIC_WRITE
			));
			if (Thread.currentThread().getName().equals("account-withdrawal")) {
				userLocked.countDown();
				if (!allowWithdrawal.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("withdrawal release timeout");
				}
			}
			return result;
		}).when(userRepository).findByIdForUpdate(deletingUser.id());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> withdrawal = executor.submit(() -> {
				Thread.currentThread().setName("account-withdrawal");
				withdrawalService.deleteMyAccount(deleteCommand(deletingUser.id()));
			});
			assertThat(userLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> join = executor.submit(() -> campusService.joinCampus(new JoinCampusCommand(
				deletingUser.id(), campus.inviteCode()
			)));

			assertThatThrownBy(() -> join.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowWithdrawal.countDown();
			withdrawal.get(5, TimeUnit.SECONDS);
			assertThatThrownBy(() -> join.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.AUTH_UNAUTHORIZED));
			assertThat(campusMemberRepository.findByUserIdOrderByIdAsc(deletingUser.id()))
				.noneMatch(member -> member.status() == CampusMemberStatus.ACTIVE);
		} finally {
			allowWithdrawal.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void login_writer_and_withdrawal_share_user_lock_so_stale_flush_cannot_revive_account() throws Exception {
		User deletingUser = saveUser("withdrawal-login-user@example.com", UserRole.USER);
		CountDownLatch tokenIssued = new CountDownLatch(1);
		CountDownLatch allowLoginCommit = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			if (Thread.currentThread().getName().equals("stale-login")) {
				tokenIssued.countDown();
				if (!allowLoginCommit.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("login release timeout");
				}
			}
			return result;
		}).when(tokenIssuanceSupport).issue(org.mockito.ArgumentMatchers.any(User.class));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> login = executor.submit(() -> {
				Thread.currentThread().setName("stale-login");
				loginService.login(new LoginCommand(deletingUser.email(), "1234"));
			});
			assertThat(tokenIssued.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> withdrawal = executor.submit(() -> withdrawalService.deleteMyAccount(
				deleteCommand(deletingUser.id())
			));

			assertThatThrownBy(() -> withdrawal.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowLoginCommit.countDown();
			login.get(5, TimeUnit.SECONDS);
			withdrawal.get(5, TimeUnit.SECONDS);
			assertThat(userRepository.findById(deletingUser.id()))
				.get()
				.satisfies(user -> {
					assertThat(user.isActive()).isFalse();
					assertThat(user.deletedAt()).isNotNull();
				});
		} finally {
			allowLoginCommit.countDown();
			executor.shutdownNow();
		}
	}

	private DeleteMyAccountCommand deleteCommand(Long userId) {
		return new DeleteMyAccountCommand(
			userId,
			"test-session",
			"test-access-jti-" + userId,
			Instant.now().plusSeconds(600),
			"1234",
			"회원탈퇴"
		);
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("탈퇴동시성", email, passwordEncoder.encode("1234"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
