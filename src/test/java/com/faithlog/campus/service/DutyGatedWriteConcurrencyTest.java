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
import com.faithlog.billing.service.command.ChangeChargeStatusCommand;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.infrastructure.repository.CampusDutyAssignmentRepository;
import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
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
	private BillingService billingService;

	@Autowired
	private UserRepository userRepository;

	@MockitoSpyBean
	private CampusDutyAssignmentRepository dutyAssignmentRepository;

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
		pauseWriterAfterDutyLookup(campus.campusId(), manager.id(), dutyChecked, allowWrite);
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
		pauseWriterAfterDutyLookup(campus.campusId(), manager.id(), dutyChecked, allowWrite);
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
		pauseWriterAfterDutyLookup(campus.campusId(), manager.id(), dutyChecked, allowManualClose);
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
				.setParameter("dutyType", DutyType.COFFEE)
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
			.findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(campusId, DutyType.COFFEE, userId);
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
