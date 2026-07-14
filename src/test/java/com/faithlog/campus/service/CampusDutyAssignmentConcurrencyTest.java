package com.faithlog.campus.service;

import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.command.AssignMealDutyCommand;
import com.faithlog.campus.service.command.ChangeCampusRoleCommand;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.service.policy.CampusAccessPolicy;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.result.CampusMembershipResult;
import com.faithlog.campus.service.result.DutyAssignmentResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusDutyAssignmentRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

	@MockitoSpyBean
	private CampusDutyAssignmentRepository dutyAssignmentRepository;

	@MockitoSpyBean
	private CampusMemberRepository campusMemberRepository;

	@MockitoSpyBean
	private CampusRepository campusRepository;

	@MockitoSpyBean
	private CampusAccessPolicy campusAccessPolicy;

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

	@Test
	void coffee_revoke_and_same_user_assign_are_serialized_so_successful_assign_stays_active() throws Exception {
		verifyRevokeAndReassignAreSerialized(DutyType.COFFEE);
	}

	@Test
	void meal_revoke_and_same_user_assign_are_serialized_so_successful_assign_stays_active() throws Exception {
		verifyRevokeAndReassignAreSerialized(DutyType.MEAL);
	}

	@Test
	void member_deletion_and_duty_assignment_are_serialized_by_campus_then_duty_order() throws Exception {
		User manager = saveUser("member-delete-assign-manager@example.com", UserRole.MANAGER);
		User target = saveUser("member-delete-assign-target@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "회원삭제지정직렬화캠", "분당", "회원 삭제와 담당 지정 동시성 RED"
		));
		CampusMembershipResult membership = campusService.joinCampus(new JoinCampusCommand(
			target.id(), campus.inviteCode()
		));
		CountDownLatch campusLocked = new CountDownLatch(1);
		CountDownLatch allowDelete = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = java.util.Optional.ofNullable(entityManager.find(
				Campus.class,
				campus.campusId(),
				LockModeType.PESSIMISTIC_WRITE
			));
			if (Thread.currentThread().getName().equals("member-delete-before-assign")) {
				campusLocked.countDown();
				if (!allowDelete.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("member delete release timeout");
				}
			}
			return result;
		}).when(campusRepository).findByIdForUpdate(campus.campusId());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> deletion = executor.submit(() -> {
				Thread.currentThread().setName("member-delete-before-assign");
				campusService.deleteCampusMember(campus.campusId(), membership.membershipId(), manager.id());
			});
			assertThat(campusLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<DutyAssignmentResult> assignment = executor.submit(() -> campusService.assignCoffeeDuty(
				new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), target.id())
			));

			assertThatThrownBy(() -> assignment.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowDelete.countDown();
			deletion.get(5, TimeUnit.SECONDS);
			assertThatThrownBy(() -> assignment.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class);
			assertThat(campusMemberRepository.findById(membership.membershipId()))
				.get()
				.matches(member -> !member.isActive());
			assertThat(campusService.getDutyAssignments(campus.campusId(), manager.id()))
				.extracting(DutyAssignmentResult::userId)
				.doesNotContain(target.id());
		} finally {
			allowDelete.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void member_deletion_waits_for_duty_revoke_in_campus_then_duty_order() throws Exception {
		User manager = saveUser("member-delete-revoke-manager@example.com", UserRole.MANAGER);
		User target = saveUser("member-delete-revoke-target@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "회원삭제해제직렬화캠", "분당", "회원 삭제와 담당 해제 동시성 RED"
		));
		CampusMembershipResult membership = campusService.joinCampus(new JoinCampusCommand(
			target.id(), campus.inviteCode()
		));
		DutyAssignmentResult assignment = campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(
			campus.campusId(), manager.id(), target.id()
		));
		CountDownLatch dutyLocked = new CountDownLatch(1);
		CountDownLatch allowRevoke = new CountDownLatch(1);
		doAnswer(invocation -> {
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
				.setParameter("userId", target.id())
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.getResultStream()
				.findFirst();
			if (Thread.currentThread().getName().equals("member-duty-revoke")) {
				dutyLocked.countDown();
				if (!allowRevoke.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("duty revoke release timeout");
				}
			}
			return result;
		}).when(dutyAssignmentRepository).findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			campus.campusId(), DutyType.COFFEE, target.id());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> revoke = executor.submit(() -> {
				Thread.currentThread().setName("member-duty-revoke");
				campusService.revokeCoffeeDuty(campus.campusId(), assignment.assignmentId(), manager.id());
			});
			assertThat(dutyLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> deletion = executor.submit(() -> campusService.deleteCampusMember(
				campus.campusId(), membership.membershipId(), manager.id()
			));

			assertThatThrownBy(() -> deletion.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowRevoke.countDown();
			revoke.get(5, TimeUnit.SECONDS);
			deletion.get(5, TimeUnit.SECONDS);
			assertThat(campusMemberRepository.findById(membership.membershipId()))
				.get()
				.matches(member -> !member.isActive());
			assertThat(campusService.getDutyAssignments(campus.campusId(), manager.id()))
				.extracting(DutyAssignmentResult::assignmentId)
				.doesNotContain(assignment.assignmentId());
		} finally {
			allowRevoke.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void member_deletion_waits_for_role_change_and_cannot_be_revived_by_stale_entity_flush() throws Exception {
		User manager = saveUser("member-delete-role-manager@example.com", UserRole.MANAGER);
		User target = saveUser("member-delete-role-target@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "회원삭제역할직렬화캠", "분당", "회원 삭제와 역할 변경 동시성 RED"
		));
		CampusMembershipResult membership = campusService.joinCampus(new JoinCampusCommand(
			target.id(), campus.inviteCode()
		));
		CountDownLatch roleChanged = new CountDownLatch(1);
		CountDownLatch allowRoleCommit = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			if (Thread.currentThread().getName().equals("member-role-change")) {
				roleChanged.countDown();
				if (!allowRoleCommit.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("role change release timeout");
				}
			}
			return result;
		}).when(campusAccessPolicy).getUsersForUpdate(List.of(manager.id(), target.id()));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> roleChange = executor.submit(() -> {
				Thread.currentThread().setName("member-role-change");
				campusService.changeCampusRole(new ChangeCampusRoleCommand(
					campus.campusId(), membership.membershipId(), manager.id(), CampusRole.ELDER
				));
			});
			assertThat(roleChanged.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> deletion = executor.submit(() -> campusService.deleteCampusMember(
				campus.campusId(), membership.membershipId(), manager.id()
			));

			assertThatThrownBy(() -> deletion.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowRoleCommit.countDown();
			roleChange.get(5, TimeUnit.SECONDS);
			deletion.get(5, TimeUnit.SECONDS);
			assertThat(campusMemberRepository.findById(membership.membershipId()))
				.get()
				.matches(member -> !member.isActive());
		} finally {
			allowRoleCommit.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	void member_deletion_revalidates_requester_role_after_waiting_for_campus_lock() throws Exception {
		User manager = saveUser("member-delete-auth-manager@example.com", UserRole.MANAGER);
		User target = saveUser("member-delete-auth-target@example.com", UserRole.USER);
		User admin = saveUser("member-delete-auth-admin@example.com", UserRole.ADMIN);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), "회원삭제권한재검증캠", "분당", "잠금 대기 뒤 관리자 권한 재검증 RED"
		));
		CampusMembershipResult targetMembership = campusService.joinCampus(new JoinCampusCommand(
			target.id(), campus.inviteCode()
		));
		Long managerMembershipId = campusMemberRepository
			.findByCampusIdAndUserId(campus.campusId(), manager.id())
			.orElseThrow()
			.id();
		CountDownLatch roleChanged = new CountDownLatch(1);
		CountDownLatch allowRoleCommit = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = invocation.callRealMethod();
			if (Thread.currentThread().getName().equals("requester-role-demotion")) {
				roleChanged.countDown();
				if (!allowRoleCommit.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("requester role demotion release timeout");
				}
			}
			return result;
		}).when(campusAccessPolicy).getUsersForUpdate(List.of(admin.id(), manager.id()));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> roleDemotion = executor.submit(() -> {
				Thread.currentThread().setName("requester-role-demotion");
				campusService.changeCampusRole(new ChangeCampusRoleCommand(
					campus.campusId(), managerMembershipId, admin.id(), CampusRole.MEMBER
				));
			});
			assertThat(roleChanged.await(5, TimeUnit.SECONDS)).isTrue();
			Future<?> deletion = executor.submit(() -> {
				Thread.currentThread().setName("member-delete-stale-manager");
				campusService.deleteCampusMember(
					campus.campusId(), targetMembership.membershipId(), manager.id()
				);
			});
			assertThatThrownBy(() -> deletion.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowRoleCommit.countDown();
			roleDemotion.get(5, TimeUnit.SECONDS);

			assertThatThrownBy(() -> deletion.get(5, TimeUnit.SECONDS))
				.hasCauseInstanceOf(BusinessException.class)
				.satisfies(exception -> assertThat(((BusinessException) exception.getCause()).errorCode())
					.isEqualTo(ErrorCode.CAMPUS_MEMBER_MANAGE_FORBIDDEN));
			assertThat(campusMemberRepository.findById(targetMembership.membershipId()))
				.get()
				.matches(member -> member.status() == com.faithlog.campus.domain.type.CampusMemberStatus.ACTIVE);
		} finally {
			allowRoleCommit.countDown();
			executor.shutdownNow();
		}
	}

	private void verifyRevokeAndReassignAreSerialized(DutyType dutyType) throws Exception {
		String label = dutyType.name().toLowerCase();
		User manager = saveUser(label + "-reassign-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(), label + "재지정직렬화캠", "분당", label + " 해제 재지정 동시성"));
		DutyAssignmentResult original = dutyType == DutyType.COFFEE
			? campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), manager.id()))
			: campusService.assignMealDuty(new AssignMealDutyCommand(campus.campusId(), manager.id(), manager.id()));
		CountDownLatch revokeLocked = new CountDownLatch(1);
		CountDownLatch allowRevoke = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object result = entityManager.createQuery("""
				select assignment
				from CampusDutyAssignment assignment
				where assignment.campusId = :campusId
					and assignment.dutyType = :dutyType
					and assignment.userId = :userId
					and assignment.isActive = true
				""", CampusDutyAssignment.class)
				.setParameter("campusId", campus.campusId())
				.setParameter("dutyType", dutyType)
				.setParameter("userId", manager.id())
				.setLockMode(LockModeType.PESSIMISTIC_WRITE)
				.getResultStream()
				.findFirst();
			if (Thread.currentThread().getName().equals("duty-revoke")) {
				revokeLocked.countDown();
				if (!allowRevoke.await(5, TimeUnit.SECONDS)) {
					throw new IllegalStateException("revoke release timeout");
				}
			}
			return result;
		}).when(dutyAssignmentRepository).findActiveByCampusIdAndDutyTypeAndUserIdForUpdate(
			campus.campusId(), dutyType, manager.id());

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> revoke = executor.submit(() -> {
				Thread.currentThread().setName("duty-revoke");
				if (dutyType == DutyType.COFFEE) {
					campusService.revokeCoffeeDuty(campus.campusId(), original.assignmentId(), manager.id());
				} else {
					campusService.revokeMealDuty(campus.campusId(), original.assignmentId(), manager.id());
				}
			});
			assertThat(revokeLocked.await(5, TimeUnit.SECONDS)).isTrue();
			Future<DutyAssignmentResult> reassign = executor.submit(() -> dutyType == DutyType.COFFEE
				? campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), manager.id()))
				: campusService.assignMealDuty(new AssignMealDutyCommand(campus.campusId(), manager.id(), manager.id())));

			assertThatThrownBy(() -> reassign.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);
			allowRevoke.countDown();
			revoke.get(5, TimeUnit.SECONDS);
			DutyAssignmentResult reassigned = reassign.get(5, TimeUnit.SECONDS);
			assertThat(reassigned.active()).isTrue();
			assertThat(campusService.getDutyAssignments(campus.campusId(), manager.id()))
				.filteredOn(result -> result.dutyType().equals(dutyType.name())
					&& result.userId().equals(manager.id()))
				.singleElement()
				.satisfies(result -> assertThat(result.assignmentId()).isNotEqualTo(original.assignmentId()));
		} finally {
			allowRevoke.countDown();
			executor.shutdownNow();
		}
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
