package com.faithlog.campus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.billing.service.BillingService;
import com.faithlog.billing.service.MealPaymentAccountService;
import com.faithlog.billing.service.command.ChangeChargeStatusCommand;
import com.faithlog.billing.service.command.CompleteChargePaymentCommand;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.infrastructure.repository.CampusDutyAssignmentRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.admin.service.AdminManagementService;
import com.faithlog.admin.service.command.ChangeUserRoleCommand;
import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.command.AssignMealDutyCommand;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.batch.service.DueCoffeePollClosureService;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.service.PollStatusCommandService;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.time.Instant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DutyGatedWriteConcurrencyTest {

	@Autowired
	private CampusService campusService;

	@Autowired
	private AdminManagementService adminManagementService;

	@Autowired
	private BillingService billingService;

	@Autowired
	private MealPaymentAccountService mealPaymentAccountService;

	@MockitoSpyBean
	private UserRepository userRepository;

	@MockitoSpyBean
	private CampusDutyAssignmentRepository dutyAssignmentRepository;

	@MockitoSpyBean
	private CampusMemberRepository campusMemberRepository;

	@MockitoSpyBean
	private CampusRepository campusRepository;

	@Autowired
	private PaymentAccountRepository paymentAccountRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private PollRepository pollRepository;

	@Autowired
	private PollStatusCommandService pollStatusCommandService;

	@Autowired
	private DueCoffeePollClosureService dueCoffeePollClosureService;

	@Autowired
	private EntityManager entityManager;

	@Test
	void coffee_account_create_holds_duty_lock_until_commit_before_revoke() throws Exception {
		User manager = saveUser("duty-write-account-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "200계좌명령동시성캠");
		DutyAssignmentResult assignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), manager.id(), manager.id()
		));
		CountDownLatch dutyChecked = new CountDownLatch(1);
		CountDownLatch allowWrite = new CountDownLatch(1);
		pauseWriterAfterDutyLookup(campus.campusId(), manager.id(), DutyType.COFFEE, dutyChecked, allowWrite);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> writer = executor.submit(() -> {
				Thread.currentThread().setName("duty-writer");
				billingService.createPaymentAccount(new CreatePaymentAccountCommand(
					campus.campusId(), manager.id(), PaymentCategory.COFFEE, "동시 계좌", "하나은행",
					"200-WRITE-ACCOUNT", "담당자", manager.id()
				));
			});
			if (!dutyChecked.await(5, TimeUnit.SECONDS)) {
				writer.get(1, TimeUnit.SECONDS);
				throw new AssertionError("담당 잠금 조회가 실행되지 않았습니다.");
			}
			Future<?> revoke = executor.submit(() -> {
				Thread.currentThread().setName("duty-revoke");
				campusService.revokeCoffeeDuty(campus.campusId(), assignment.assignmentId(), manager.id());
			});

			assertThatThrownBy(() -> revoke.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowWrite.countDown();
			writer.get(5, TimeUnit.SECONDS);
			revoke.get(5, TimeUnit.SECONDS);
			assertThat(dutyAssignmentRepository.findById(assignment.assignmentId()))
				.get()
				.extracting(CampusDutyAssignment::isActive)
				.isEqualTo(false);
		} finally {
			allowWrite.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void concurrent_coffee_account_delete_revalidates_deleted_state_after_duty_lock() throws Exception {
		User manager = saveUser("duty-account-delete-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "200계좌삭제최신상태캠");
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), manager.id(), manager.id()));
		Long accountId = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.COFFEE, "삭제 계좌", "하나은행",
			"200-DELETE-ACCOUNT", "담당자", manager.id())).id();
		billingService.deactivatePaymentAccount(accountId, manager.id());
		CountDownLatch firstHasDutyLock = new CountDownLatch(1);
		CountDownLatch secondReachedDutyLock = new CountDownLatch(1);
		CountDownLatch allowFirstDelete = new CountDownLatch(1);
		doAnswer(invocation -> {
			if (Thread.currentThread().getName().equals("second-account-delete")) {
				secondReachedDutyLock.countDown();
			}
			Object result = entityManager.createQuery("""
				select assignment
				from CampusDutyAssignment assignment
				where assignment.campusId = :campusId
					and assignment.dutyType = :dutyType
					and assignment.userId = :userId
					and assignment.isActive = true
				""", CampusDutyAssignment.class)
				.setParameter("campusId", campus.campusId())
				.setParameter("dutyType", DutyType.COFFEE)
				.setParameter("userId", manager.id())
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.getResultStream()
				.findFirst();
			if (Thread.currentThread().getName().equals("first-account-delete")) {
				firstHasDutyLock.countDown();
				if (!allowFirstDelete.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Timed out while coordinating account delete.");
				}
			}
			return result;
		}).when(dutyAssignmentRepository)
			.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
				campus.campusId(), DutyType.COFFEE, manager.id());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> first = executor.submit(() -> {
				Thread.currentThread().setName("first-account-delete");
				billingService.deletePaymentAccount(campus.campusId(), accountId, manager.id());
			});
			assertThat(firstHasDutyLock.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> second = executor.submit(() -> {
				Thread.currentThread().setName("second-account-delete");
				billingService.deletePaymentAccount(campus.campusId(), accountId, manager.id());
			});
			assertThat(secondReachedDutyLock.await(5, TimeUnit.SECONDS)).isTrue();

			allowFirstDelete.countDown();
			first.get(5, TimeUnit.SECONDS);
			assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.BILLING_PAYMENT_ACCOUNT_NOT_FOUND));
		} finally {
			allowFirstDelete.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void meal_account_deactivate_holds_duty_lock_until_commit_before_revoke() throws Exception {
		User manager = saveUser("duty-write-meal-account-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "200밥계좌명령동시성캠");
		DutyAssignmentResult assignment = campusService.assignMealDuty(new AssignMealDutyCommand(
			campus.campusId(), manager.id(), manager.id()
		));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.MEAL, "동시 밥 계좌", "하나은행", "200-MEAL-ACCOUNT",
			"담당자", manager.id()
		));
		CountDownLatch dutyChecked = new CountDownLatch(1);
		CountDownLatch allowWrite = new CountDownLatch(1);
		pauseWriterAfterDutyLookup(campus.campusId(), manager.id(), DutyType.MEAL, dutyChecked, allowWrite);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> writer = executor.submit(() -> {
				Thread.currentThread().setName("duty-writer");
				mealPaymentAccountService.deactivate(campus.campusId(), account.id(), manager.id());
			});
			if (!dutyChecked.await(5, TimeUnit.SECONDS)) {
				writer.get(1, TimeUnit.SECONDS);
				throw new AssertionError("밥 담당 잠금 조회가 실행되지 않았습니다.");
			}
			Future<?> revoke = executor.submit(() -> {
				Thread.currentThread().setName("duty-revoke");
				campusService.revokeMealDuty(campus.campusId(), assignment.assignmentId(), manager.id());
			});

			assertThatThrownBy(() -> revoke.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowWrite.countDown();
			writer.get(5, TimeUnit.SECONDS);
			revoke.get(5, TimeUnit.SECONDS);
			assertThat(paymentAccountRepository.findAllById(java.util.List.of(account.id())))
				.singleElement()
				.extracting(PaymentAccount::isActive)
				.isEqualTo(false);
			assertThat(dutyAssignmentRepository.findById(assignment.assignmentId()))
				.get()
				.extracting(CampusDutyAssignment::isActive)
				.isEqualTo(false);
		} finally {
			allowWrite.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void coffee_charge_unpaid_transition_holds_duty_lock_so_revoke_observes_new_unpaid() throws Exception {
		User manager = saveUser("duty-write-charge-manager@example.com", UserRole.MANAGER);
		User target = saveUser("duty-write-charge-target@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "200청구명령동시성캠");
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		DutyAssignmentResult assignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), manager.id(), manager.id()
		));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "청구 계좌", "하나은행", "200-WRITE-CHARGE",
			"담당자", manager.id()
		));
		ChargeItem charge = ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, account.id(), account.bankName(),
			account.accountNumber(), account.accountHolder(), ChargeSourceType.POLL_RESPONSE, 200900L,
			"커피 주문", "동시성 검증", 1800, null
		);
		charge.markPaid();
		chargeItemRepository.saveAndFlush(charge);
		CountDownLatch dutyChecked = new CountDownLatch(1);
		CountDownLatch allowWrite = new CountDownLatch(1);
		pauseWriterAfterDutyLookup(campus.campusId(), manager.id(), DutyType.COFFEE, dutyChecked, allowWrite);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> writer = executor.submit(() -> {
				Thread.currentThread().setName("duty-writer");
				billingService.changeChargeStatus(new ChangeChargeStatusCommand(
					charge.id(), manager.id(), ChargeStatus.UNPAID
				));
			});
			if (!dutyChecked.await(5, TimeUnit.SECONDS)) {
				writer.get(1, TimeUnit.SECONDS);
				throw new AssertionError("담당 잠금 조회가 실행되지 않았습니다.");
			}
			Future<?> revoke = executor.submit(() -> {
				Thread.currentThread().setName("duty-revoke");
				campusService.revokeCoffeeDuty(campus.campusId(), assignment.assignmentId(), manager.id());
			});

			assertThatThrownBy(() -> revoke.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowWrite.countDown();
			writer.get(5, TimeUnit.SECONDS);
			assertThatThrownBy(() -> revoke.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.CAMPUS_COFFEE_DUTY_UNPAID_CHARGE_CONFLICT));
			assertThat(chargeItemRepository.findById(charge.id()))
				.get()
				.extracting(ChargeItem::status)
				.isEqualTo(ChargeStatus.UNPAID);
		} finally {
			allowWrite.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void coffee_charge_status_change_uses_latest_member_payment_after_waiting_for_duty_lock() throws Exception {
		User manager = saveUser("duty-write-stale-charge-manager@example.com", UserRole.MANAGER);
		User target = saveUser("duty-write-stale-charge-target@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "200청구최신상태동시성캠");
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), manager.id(), manager.id()
		));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "최신 상태 계좌", "하나은행", "200-STALE-CHARGE",
			"담당자", manager.id()
		));
		ChargeItem charge = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, account.id(), account.bankName(),
			account.accountNumber(), account.accountHolder(), ChargeSourceType.POLL_RESPONSE, 200901L,
			"커피 주문", "최신 상태 검증", 1800, null
		));
		CountDownLatch dutyChecked = new CountDownLatch(1);
		CountDownLatch allowWrite = new CountDownLatch(1);
		pauseWriterAfterDutyLookup(campus.campusId(), manager.id(), DutyType.COFFEE, dutyChecked, allowWrite);
		Instant paidAt = Instant.now().minusSeconds(30);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> writer = executor.submit(() -> {
				Thread.currentThread().setName("duty-writer");
				billingService.changeChargeStatus(new ChangeChargeStatusCommand(
					charge.id(), manager.id(), ChargeStatus.WAIVED
				));
			});
			if (!dutyChecked.await(5, TimeUnit.SECONDS)) {
				writer.get(1, TimeUnit.SECONDS);
				throw new AssertionError("담당 잠금 조회가 실행되지 않았습니다.");
			}

			billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
				campus.campusId(), charge.id(), target.id(), paidAt
			));
			allowWrite.countDown();

			assertThatThrownBy(() -> writer.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.BILLING_CHARGE_STATUS_TRANSITION_CONFLICT));
			assertThat(chargeItemRepository.findById(charge.id()))
				.get()
				.satisfies(saved -> {
					assertThat(saved.status()).isEqualTo(ChargeStatus.PAID);
					assertThat(saved.paidAt()).isEqualTo(paidAt);
				});
		} finally {
			allowWrite.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void stale_duty_recovery_cannot_reopen_latest_member_payment_as_unpaid() throws Exception {
		User owner = saveUser("stale-recovery-latest-owner@example.com", UserRole.MANAGER);
		User target = saveUser("stale-recovery-latest-target@example.com", UserRole.USER);
		User admin = saveUser("stale-recovery-latest-admin@example.com", UserRole.ADMIN);
		CampusCreateResult campus = createCampus(owner, "200과거담당최신납부캠");
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), owner.id(), owner.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "과거 담당 계좌", "하나은행", "200-STALE-LATEST",
			"과거담당", owner.id()
		));
		ChargeItem charge = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, account.id(), account.bankName(),
			account.accountNumber(), account.accountHolder(), ChargeSourceType.POLL_RESPONSE, 200902L,
			"커피 주문", "과거 담당 최신 납부 검증", 1800, null
		));
		CampusMember ownerMembership = campusMemberRepository
			.findByCampusIdAndUserId(campus.campusId(), owner.id()).orElseThrow();
		ownerMembership.deactivate();
		campusMemberRepository.saveAndFlush(ownerMembership);

		CountDownLatch dutyChecked = new CountDownLatch(1);
		CountDownLatch allowRecovery = new CountDownLatch(1);
		pauseWriterAfterDutyLookup(campus.campusId(), owner.id(), DutyType.COFFEE, dutyChecked, allowRecovery);
		Instant paidAt = Instant.now().minusSeconds(15);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> recovery = executor.submit(() -> {
				Thread.currentThread().setName("duty-writer");
				billingService.changeChargeStatus(new ChangeChargeStatusCommand(
					charge.id(), admin.id(), ChargeStatus.WAIVED
				));
			});
			assertThat(dutyChecked.await(5, TimeUnit.SECONDS)).isTrue();

			billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
				campus.campusId(), charge.id(), target.id(), paidAt
			));
			allowRecovery.countDown();

			assertThatThrownBy(() -> recovery.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.BILLING_CHARGE_STATUS_TRANSITION_CONFLICT));
			assertThat(chargeItemRepository.findById(charge.id()))
				.get()
				.satisfies(saved -> {
					assertThat(saved.status()).isEqualTo(ChargeStatus.PAID);
					assertThat(saved.paidAt()).isEqualTo(paidAt);
				});
		} finally {
			allowRecovery.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void stale_recovery_serializes_requester_admin_demotion_before_campus_lock() throws Exception {
		User owner = saveUser("stale-admin-role-owner@example.com", UserRole.MANAGER);
		User target = saveUser("stale-admin-role-target@example.com", UserRole.USER);
		User recoveryAdmin = saveUser("stale-admin-role-requester@example.com", UserRole.ADMIN);
		User guardAdmin = saveUser("stale-admin-role-guard@example.com", UserRole.ADMIN);
		CampusCreateResult campus = createCampus(owner, "200복구요청자강등직렬화캠");
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), owner.id(), owner.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "복구 요청자 계좌", "하나은행", "200-ADMIN-ROLE",
			"과거담당", owner.id()));
		ChargeItem charge = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, account.id(), account.bankName(),
			account.accountNumber(), account.accountHolder(), ChargeSourceType.POLL_RESPONSE, 200904L,
			"커피 주문", "복구 요청자 강등 검증", 1800, null));
		CampusMember ownerMembership = campusMemberRepository
			.findByCampusIdAndUserId(campus.campusId(), owner.id()).orElseThrow();
		ownerMembership.deactivate();
		campusMemberRepository.saveAndFlush(ownerMembership);

		CountDownLatch campusBoundaryReached = new CountDownLatch(1);
		CountDownLatch allowCampusLock = new CountDownLatch(1);
		doAnswer(invocation -> {
			if (Thread.currentThread().getName().equals("stale-admin-recovery")) {
				campusBoundaryReached.countDown();
				if (!allowCampusLock.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("campus lock release timeout");
				}
			}
			return java.util.Optional.ofNullable(entityManager.find(
				Campus.class, campus.campusId(), LockModeType.PESSIMISTIC_WRITE));
		}).when(campusRepository).findByIdForUpdate(campus.campusId());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> recovery = executor.submit(() -> {
				Thread.currentThread().setName("stale-admin-recovery");
				billingService.changeChargeStatus(new ChangeChargeStatusCommand(
					charge.id(), recoveryAdmin.id(), ChargeStatus.WAIVED));
			});
			assertThat(campusBoundaryReached.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> demotion = executor.submit(() -> adminManagementService.changeUserRole(
				new ChangeUserRoleCommand(guardAdmin.id(), recoveryAdmin.id(), UserRole.USER)));

			assertThatThrownBy(() -> demotion.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowCampusLock.countDown();
			recovery.get(5, TimeUnit.SECONDS);
			demotion.get(5, TimeUnit.SECONDS);
			assertThat(chargeItemRepository.findById(charge.id()))
				.get().extracting(ChargeItem::status).isEqualTo(ChargeStatus.WAIVED);
			assertThat(userRepository.findById(recoveryAdmin.id()))
				.get().extracting(User::role).isEqualTo(UserRole.USER);
		} finally {
			allowCampusLock.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void stale_recovery_reads_latest_admin_role_when_demotion_commits_before_user_lock() throws Exception {
		User owner = saveUser("stale-role-refresh-owner@example.com", UserRole.MANAGER);
		User target = saveUser("stale-role-refresh-target@example.com", UserRole.USER);
		User recoveryAdmin = saveUser("stale-role-refresh-requester@example.com", UserRole.ADMIN);
		User guardAdmin = saveUser("stale-role-refresh-guard@example.com", UserRole.ADMIN);
		CampusCreateResult campus = createCampus(owner, "200복구요청자최신역할캠");
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), owner.id(), owner.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "최신 역할 계좌", "하나은행", "200-ROLE-REFRESH",
			"과거담당", owner.id()));
		ChargeItem charge = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, account.id(), account.bankName(),
			account.accountNumber(), account.accountHolder(), ChargeSourceType.POLL_RESPONSE, 200905L,
			"커피 주문", "복구 요청자 최신 역할 검증", 1800, null));
		CampusMember ownerMembership = campusMemberRepository
			.findByCampusIdAndUserId(campus.campusId(), owner.id()).orElseThrow();
		ownerMembership.deactivate();
		campusMemberRepository.saveAndFlush(ownerMembership);

		CountDownLatch userLockBoundary = new CountDownLatch(1);
		CountDownLatch allowUserLock = new CountDownLatch(1);
		doAnswer(invocation -> {
			if (Thread.currentThread().getName().equals("stale-role-refresh")) {
				userLockBoundary.countDown();
				if (!allowUserLock.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("user lock release timeout");
				}
			}
			return java.util.Optional.ofNullable(entityManager.find(
				User.class, recoveryAdmin.id(), LockModeType.PESSIMISTIC_WRITE));
		}).when(userRepository).findByIdForUpdate(recoveryAdmin.id());

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> recovery = executor.submit(() -> {
				Thread.currentThread().setName("stale-role-refresh");
				billingService.changeChargeStatus(new ChangeChargeStatusCommand(
					charge.id(), recoveryAdmin.id(), ChargeStatus.WAIVED));
			});
			assertThat(userLockBoundary.await(5, TimeUnit.SECONDS)).isTrue();
			adminManagementService.changeUserRole(new ChangeUserRoleCommand(
				guardAdmin.id(), recoveryAdmin.id(), UserRole.USER));
			allowUserLock.countDown();

			assertThatThrownBy(() -> recovery.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN));
			assertThat(chargeItemRepository.findById(charge.id()))
				.get().extracting(ChargeItem::status).isEqualTo(ChargeStatus.UNPAID);
		} finally {
			allowUserLock.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void stale_recovery_and_idempotent_assignment_do_not_deadlock_member_and_duty_locks() throws Exception {
		User owner = saveUser("stale-lock-order-owner@example.com", UserRole.MANAGER);
		User target = saveUser("stale-lock-order-target@example.com", UserRole.USER);
		User admin = saveUser("stale-lock-order-admin@example.com", UserRole.ADMIN);
		CampusCreateResult campus = createCampus(owner, "200과거복구잠금순서캠");
		campusService.joinCampus(new JoinCampusCommand(target.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), owner.id(), owner.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "잠금 순서 계좌", "하나은행", "200-STALE-ORDER",
			"담당자", owner.id()
		));
		ChargeItem charge = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(), target.id(), PaymentCategory.COFFEE, account.id(), account.bankName(),
			account.accountNumber(), account.accountHolder(), ChargeSourceType.POLL_RESPONSE, 200903L,
			"커피 주문", "잠금 순서 검증", 1800, null
		));

		CountDownLatch memberLocked = new CountDownLatch(1);
		CountDownLatch allowAssignment = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = entityManager.createQuery("""
				select member from CampusMember member
				where member.campusId = :campusId and member.userId = :userId
				""", CampusMember.class)
				.setParameter("campusId", campus.campusId())
				.setParameter("userId", owner.id())
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.getResultStream()
				.findFirst();
			if (Thread.currentThread().getName().equals("duty-assign")) {
				memberLocked.countDown();
				if (!allowAssignment.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("assignment release timeout");
				}
			}
			return result;
		}).when(campusMemberRepository)
			.findByCampusIdAndUserIdForUpdate(campus.campusId(), owner.id());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> assignment = executor.submit(() -> {
				Thread.currentThread().setName("duty-assign");
				campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
					campus.campusId(), owner.id(), owner.id()));
			});
			assertThat(memberLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> recovery = executor.submit(() -> {
				Thread.currentThread().setName("stale-recovery");
				billingService.changeChargeStatus(new ChangeChargeStatusCommand(
					charge.id(), admin.id(), ChargeStatus.WAIVED
				));
			});

			assertThatThrownBy(() -> recovery.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowAssignment.countDown();
			assignment.get(5, TimeUnit.SECONDS);
			assertThatThrownBy(() -> recovery.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.BILLING_CHARGE_STATUS_MANAGE_FORBIDDEN));
		} finally {
			allowAssignment.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void manual_and_due_coffee_close_serialize_on_duty_before_poll() throws Exception {
		User manager = saveUser("duty-close-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "200마감잠금동시성캠");
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), manager.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(), PaymentCategory.COFFEE, "마감 계좌", "하나은행", "200-CLOSE-ACCOUNT",
			"담당자", manager.id()
		));
		Instant now = Instant.now();
		Poll poll = Poll.create(
			campus.campusId(), null, "동시 마감 투표", PollType.COFFEE, SelectionType.SINGLE, false, true,
			ChargeGenerationType.OPTION_PRICE, PaymentCategory.COFFEE, account.id(),
			now.minusSeconds(3600), now.minusSeconds(60), manager.id()
		);
		poll.open();
		pollRepository.saveAndFlush(poll);
		CountDownLatch dutyChecked = new CountDownLatch(1);
		CountDownLatch allowManualClose = new CountDownLatch(1);
		pauseWriterAfterDutyLookup(campus.campusId(), manager.id(), DutyType.COFFEE, dutyChecked, allowManualClose);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> manualClose = executor.submit(() -> {
				Thread.currentThread().setName("duty-writer");
				pollStatusCommandService.closePoll(campus.campusId(), poll.id(), manager.id());
			});
			assertThat(dutyChecked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Integer> dueClose = executor.submit(() -> {
				Thread.currentThread().setName("due-closer");
				return dueCoffeePollClosureService.closeDueCoffeePolls(now);
			});

			assertThatThrownBy(() -> dueClose.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowManualClose.countDown();
			manualClose.get(5, TimeUnit.SECONDS);
			assertThat(dueClose.get(5, TimeUnit.SECONDS)).isZero();
		} finally {
			allowManualClose.countDown();
			executor.shutdownNow();
		}
	}

	private void pauseWriterAfterDutyLookup(
		Long campusId,
		Long userId,
		DutyType dutyType,
		CountDownLatch dutyChecked,
		CountDownLatch allowWrite
	) {
		Answer<Object> answer = invocation -> {
			Object result = entityManager.createQuery("""
				select assignment
				from CampusDutyAssignment assignment
				where assignment.campusId = :campusId
					and assignment.dutyType = :dutyType
					and assignment.userId = :userId
					and assignment.isActive = true
				""", CampusDutyAssignment.class)
				.setParameter("campusId", campusId)
				.setParameter("dutyType", dutyType)
				.setParameter("userId", userId)
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.getResultStream()
				.findFirst();
			if (Thread.currentThread().getName().equals("duty-writer")) {
				dutyChecked.countDown();
				if (!allowWrite.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("Timed out while coordinating duty-gated write.");
				}
			}
			return result;
		};
		doAnswer(answer).when(dutyAssignmentRepository)
			.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(campusId, dutyType, userId);
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("동시성테스트", email, "dummy-password-hash");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(
			manager.id(), name, "분당", name + " 설명"
		));
	}
}
