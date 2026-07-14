package com.faithlog.campus.service;

import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.command.AssignMealDutyCommand;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
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
import java.util.concurrent.TimeoutException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CampusDutyAssignmentConcurrencyTest {

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PaymentAccountRepository paymentAccountRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private EntityManager entityManager;

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

	@Test
	void revokeCoffeeDuty_waits_for_settlement_lock_then_rejects_new_unpaid_charge() throws Exception {
		verifyRevokeWaitsForSettlement(PaymentCategory.COFFEE);
	}

	@Test
	void revokeMealDuty_waits_for_settlement_lock_then_rejects_new_unpaid_charge() throws Exception {
		verifyRevokeWaitsForSettlement(PaymentCategory.MEAL);
	}

	private void verifyRevokeWaitsForSettlement(PaymentCategory category) throws Exception {
		String label = category.name().toLowerCase();
		User manager = saveUser(label + "-revoke-manager@example.com", UserRole.MANAGER);
		User duty = saveUser(label + "-revoke-duty@example.com", UserRole.USER);
		User target = saveUser(label + "-revoke-target@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), label + "동시해제캠", "분당", label + " 담당 해제 동시성"
		));
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		DutyAssignmentResult assignment = category == PaymentCategory.COFFEE
			? campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()))
			: campusService.assignMealDuty(new AssignMealDutyCommand(campus.campusId(), manager.id(), duty.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), category, label + " 계좌", "하나은행", label + "-200", "담당자", duty.id()
		));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch settlementLocked = new CountDownLatch(1);
		CountDownLatch allowSettlement = new CountDownLatch(1);
		Future<?> settlement = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
			entityManager.find(
				CampusDutyAssignment.class,
				assignment.assignmentId(),
				LockModeType.PESSIMISTIC_WRITE
			);
			settlementLocked.countDown();
			try {
				if (!allowSettlement.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("settlement release timeout");
				}
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			chargeItemRepository.saveAndFlush(ChargeItem.create(
				campus.campusId(), target.id(), category, account.id(), account.bankName(), account.accountNumber(),
				account.accountHolder(), ChargeSourceType.POLL_RESPONSE, assignment.assignmentId(),
				label + " 미납", "동시 정산", 1000, null
			));
		}));
		assertThat(settlementLocked.await(5, TimeUnit.SECONDS)).isTrue();

		Future<?> revoke = executor.submit(() -> {
			if (category == PaymentCategory.COFFEE) {
				campusService.revokeCoffeeDuty(campus.campusId(), assignment.assignmentId(), manager.id());
			} else {
				campusService.revokeMealDuty(campus.campusId(), assignment.assignmentId(), manager.id());
			}
		});
		assertThatThrownBy(() -> revoke.get(300, TimeUnit.MILLISECONDS))
			.isInstanceOf(TimeoutException.class);

		allowSettlement.countDown();
		settlement.get(5, TimeUnit.SECONDS);
		assertThatThrownBy(() -> revoke.get(5, TimeUnit.SECONDS))
			.hasCauseInstanceOf(BusinessException.class)
			.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode()).isEqualTo(
				category == PaymentCategory.COFFEE
					? ErrorCode.CAMPUS_COFFEE_DUTY_UNPAID_CHARGE_CONFLICT
					: ErrorCode.CAMPUS_MEAL_DUTY_UNPAID_CHARGE_CONFLICT
			));
		executor.shutdownNow();
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("동시성테스트", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
