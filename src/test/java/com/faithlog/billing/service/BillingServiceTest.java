package com.faithlog.billing.service;

import com.faithlog.billing.service.command.ChangeChargeStatusCommand;
import com.faithlog.billing.service.command.CompleteChargePaymentCommand;
import com.faithlog.billing.service.command.CreateCoffeeChargeCommand;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.billing.service.result.PaymentAccountResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class BillingServiceTest {

	@Autowired
	private BillingService billingService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private PaymentAccountRepository paymentAccountRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void createPenaltyPaymentAccount_defaults_missing_owner_to_requester_and_preserves_explicit_owner() {
		User manager = saveUser("billing-122-penalty-owner-manager@example.com", UserRole.MANAGER);
		User treasurer = saveUser("billing-122-penalty-owner-treasurer@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "122벌금소유자캠");
		campusService.joinCampus(new JoinCampusCommand(treasurer.id(), campus.inviteCode()));

		PaymentAccountResult defaultOwner = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"기본 owner 벌금 계좌",
			"하나은행",
			"122-PENALTY-DEFAULT",
			"회계",
			null
		));
		PaymentAccountResult explicitOwner = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"명시 owner 벌금 계좌",
			"국민은행",
			"122-PENALTY-EXPLICIT",
			"담당회계",
			treasurer.id()
		));

		assertThat(defaultOwner.ownerUserId()).isEqualTo(manager.id());
		assertThat(paymentAccountRepository.getReferenceById(defaultOwner.id()).ownerUserId()).isEqualTo(manager.id());
		assertThat(explicitOwner.ownerUserId()).isEqualTo(treasurer.id());
		assertThat(paymentAccountRepository.getReferenceById(explicitOwner.id()).ownerUserId()).isEqualTo(treasurer.id());
	}

	@Test
	void createPaymentAccount_deactivates_previous_active_account_per_campus_and_type() {
		User manager = saveUser("billing-account-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "40캠");

		PaymentAccountResult first = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"이전 벌금 계좌",
			"카카오뱅크",
			"3333-00-1111111",
			"이전회계",
			manager.id()
		));
		PaymentAccountResult second = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"새 벌금 계좌",
			"국민은행",
			"111111-22-333333",
			"새회계",
			manager.id()
		));

		List<PaymentAccount> penaltyAccounts = paymentAccountRepository.findByCampusIdAndAccountTypeOrderByIdAsc(
			campus.campusId(),
			PaymentCategory.PENALTY
		);

		assertThat(second.isActive()).isTrue();
		assertThat(paymentAccountRepository.getReferenceById(first.id())).satisfies(account -> {
			assertThat(account.isActive()).isFalse();
			assertThat(account.deactivatedAt()).isNotNull();
		});
		assertThat(penaltyAccounts)
			.filteredOn(PaymentAccount::isActive)
			.extracting(PaymentAccount::id)
			.containsExactly(second.id());
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void createPaymentAccount_rolls_back_previous_deactivation_when_replacement_insert_fails() {
		User manager = saveUser("billing-account-rollback-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "계좌롤백캠");
		PaymentAccountResult previous = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"기존 벌금 계좌",
			"하나은행",
			"148-ROLLBACK-OLD",
			"기존회계",
			null
		));

		assertThatThrownBy(() -> billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"실패할 벌금 계좌",
			null,
			"148-ROLLBACK-NEW",
			"신규회계",
			null
		)))
			.isInstanceOf(DataIntegrityViolationException.class);

		PaymentAccount activeAccount = paymentAccountRepository
			.findByCampusIdAndAccountTypeAndIsActiveTrueAndDeletedAtIsNull(
				campus.campusId(),
				PaymentCategory.PENALTY
			)
			.orElseThrow();
		assertThat(activeAccount.id()).isEqualTo(previous.id());
		assertThat(activeAccount.isActive()).isTrue();
		assertThat(paymentAccountRepository.findByCampusIdAndAccountTypeOrderByIdAsc(
			campus.campusId(),
			PaymentCategory.PENALTY
		)).hasSize(1);
	}

	@Test
	void listPaymentAccounts_allows_only_active_campus_members_and_returns_active_accounts() {
		User manager = saveUser("billing-list-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-list-member@example.com", UserRole.USER);
		User admin = saveUser("billing-list-admin@example.com", UserRole.ADMIN);
		User inactive = saveUser("billing-list-inactive@example.com", UserRole.USER);
		User outsider = saveUser("billing-list-outsider@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "41캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(inactive.id(), campus.inviteCode()));
		CampusMember inactiveMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), inactive.id())
			.orElseThrow();
		inactiveMembership.deactivate();

		PaymentAccountResult active = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"카카오뱅크",
			"3333-00-2222222",
			"회계",
			null
		));
		billingService.deactivatePaymentAccount(active.id(), manager.id());

		PaymentAccountResult replacement = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"현재 벌금 계좌",
			"신한은행",
			"110-222-333333",
			"현재회계",
			null
		));

		assertThat(billingService.listPaymentAccounts(campus.campusId(), member.id()))
			.extracting(PaymentAccountResult::id)
			.containsExactly(replacement.id());
		assertThat(billingService.listPaymentAccounts(campus.campusId(), admin.id()))
			.extracting(PaymentAccountResult::id)
			.containsExactly(replacement.id());
		assertThatThrownBy(() -> billingService.listPaymentAccounts(campus.campusId(), inactive.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 납부 계좌 조회 권한이 없습니다.");
		assertThatThrownBy(() -> billingService.listPaymentAccounts(campus.campusId(), outsider.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 납부 계좌 조회 권한이 없습니다.");
	}

	@Test
	void createAndDeactivatePaymentAccount_requires_campus_manager() {
		User manager = saveUser("billing-auth-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-auth-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "42캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));

		assertThatThrownBy(() -> billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"카카오뱅크",
			"3333-00-3333333",
			"회계",
			null
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("납부 계좌 관리 권한이 없습니다.");

		PaymentAccountResult account = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"카카오뱅크",
			"3333-00-3333333",
			"회계",
			null
		));

		assertThatThrownBy(() -> billingService.deactivatePaymentAccount(account.id(), member.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("납부 계좌 관리 권한이 없습니다.");
	}

	@Test
	void coffee_duty_can_manage_only_coffee_payment_accounts_in_own_campus() {
		User manager = saveUser("billing-coffee-duty-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("billing-coffee-duty@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "42커피캠");
		CampusCreateResult otherCampus = createCampus(manager, "42다른캠");
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));

		PaymentAccountResult coffeeAccount = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			duty.id(),
			PaymentCategory.COFFEE,
			"커피 계좌",
			"카카오뱅크",
			"3333-42-000001",
			"커피회계",
			duty.id()
		));
		PaymentAccountResult deactivated = billingService.deactivatePaymentAccount(coffeeAccount.id(), duty.id());

		assertThat(coffeeAccount.accountType()).isEqualTo(PaymentCategory.COFFEE);
		assertThat(deactivated.isActive()).isFalse();
		assertThatThrownBy(() -> billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			duty.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"신한은행",
			"110-42-000001",
			"벌금회계",
			duty.id()
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("납부 계좌 관리 권한이 없습니다.");
		assertThatThrownBy(() -> billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			otherCampus.campusId(),
			duty.id(),
			PaymentCategory.COFFEE,
			"다른 캠퍼스 커피 계좌",
			"카카오뱅크",
			"3333-42-000002",
			"커피회계",
			duty.id()
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("납부 계좌 관리 권한이 없습니다.");
	}

	@Test
	void coffee_accounts_keep_active_scope_per_owner_and_do_not_reconnect_existing_charges() {
		User minister = saveUser("billing-coffee-owner-minister@example.com", UserRole.MANAGER);
		User elder = saveUser("billing-coffee-owner-elder@example.com", UserRole.USER);
		User member = saveUser("billing-coffee-owner-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(minister, "114커피소유캠");
		campusService.joinCampus(new JoinCampusCommand(elder.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		CampusMember elderMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), elder.id())
			.orElseThrow();
		ReflectionTestUtils.setField(elderMembership, "campusRole", CampusRole.ELDER);
		campusMemberRepository.saveAndFlush(elderMembership);

		PaymentAccountResult ministerFirst = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			minister.id(),
			PaymentCategory.COFFEE,
			"목사 커피 계좌",
			"하나은행",
			"114-COFFEE-1",
			"목사",
			minister.id()
		));
		ChargeItem coffeeCharge = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(),
			member.id(),
			PaymentCategory.COFFEE,
			ministerFirst.id(),
			ministerFirst.bankName(),
			ministerFirst.accountNumber(),
			ministerFirst.accountHolder(),
			ChargeSourceType.POLL_RESPONSE,
			11401L,
			"커피 주문",
			"기존 커피 정산",
			1800,
			null
		));

		PaymentAccountResult elderAccount = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			elder.id(),
			PaymentCategory.COFFEE,
			"장로 커피 계좌",
			"국민은행",
			"114-COFFEE-2",
			"장로",
			elder.id()
		));
		PaymentAccountResult ministerSecond = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			minister.id(),
			PaymentCategory.COFFEE,
			"목사 새 커피 계좌",
			"신한은행",
			"114-COFFEE-3",
			"목사",
			minister.id()
		));

		assertThat(paymentAccountRepository.getReferenceById(ministerFirst.id()).isActive()).isFalse();
		assertThat(paymentAccountRepository.getReferenceById(elderAccount.id()).isActive()).isTrue();
		assertThat(paymentAccountRepository.getReferenceById(ministerSecond.id()).isActive()).isTrue();
		assertThat(chargeItemRepository.findById(coffeeCharge.id())).get()
			.satisfies(charge -> {
				assertThat(charge.paymentAccountId()).isEqualTo(ministerFirst.id());
				assertThat(charge.accountNumberSnapshot()).isEqualTo("114-COFFEE-1");
			});
	}

	@Test
	void coffee_payment_account_creation_allows_only_requester_owned_account() {
		User manager = saveUser("billing-coffee-owner-mismatch-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-coffee-owner-mismatch-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "114커피본인계좌캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));

		assertThatThrownBy(() -> billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.COFFEE,
			"다른 사람 커피 계좌",
			"하나은행",
			"114-OWNER-MISMATCH",
			"다른사람",
			member.id()
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("본인 커피 계좌만 등록할 수 있습니다.");
	}

	@Test
	void listAdminPaymentAccounts_returns_management_metadata_with_role_scoped_visibility() {
		User manager = saveUser("billing-admin-account-manager@example.com", UserRole.MANAGER);
		User duty = saveUser("billing-admin-account-duty@example.com", UserRole.USER);
		User otherDuty = saveUser("billing-admin-account-other-duty@example.com", UserRole.USER);
		User member = saveUser("billing-admin-account-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "112관리계좌캠");
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(otherDuty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		CampusMember otherDutyMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), otherDuty.id())
			.orElseThrow();
		ReflectionTestUtils.setField(otherDutyMembership, "campusRole", CampusRole.ELDER);
		campusMemberRepository.saveAndFlush(otherDutyMembership);
		PaymentAccountResult penalty = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "벌금 계좌", "하나은행", "112-201", "벌금회계", manager.id()
		));
		PaymentAccountResult otherCoffee = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), otherDuty.id(), PaymentCategory.COFFEE, "다른 커피 계좌", "하나은행", "112-203", "커피회계", otherDuty.id()
		));
		PaymentAccountResult dutyCoffee = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), duty.id(), PaymentCategory.COFFEE, "담당자 커피 계좌", "하나은행", "112-202", "커피회계", duty.id()
		));
		billingService.deactivatePaymentAccount(penalty.id(), manager.id());

		List<PaymentAccountResult> managerResults = billingService.listAdminPaymentAccounts(campus.campusId(), manager.id());
		List<PaymentAccountResult> dutyResults = billingService.listAdminPaymentAccounts(campus.campusId(), duty.id());

		assertThat(managerResults)
			.extracting(PaymentAccountResult::id)
			.containsExactly(otherCoffee.id(), dutyCoffee.id());
		assertThat(billingService.listAdminPaymentAccounts(campus.campusId(), manager.id(), null, true))
			.filteredOn(result -> result.id().equals(penalty.id()))
			.singleElement()
			.satisfies(result -> {
				assertThat(result.isActive()).isFalse();
				assertThat(result.createdAt()).isNotNull();
				assertThat(result.deactivatedAt()).isNotNull();
			});
		assertThat(dutyResults)
			.extracting(PaymentAccountResult::id)
			.containsExactly(dutyCoffee.id());
		assertThatThrownBy(() -> billingService.listAdminPaymentAccounts(campus.campusId(), member.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 납부 계좌 조회 권한이 없습니다.");
	}

	@Test
	void listAdminPaymentAccounts_defaults_to_active_only_and_can_include_inactive_penalty_accounts() {
		User manager = saveUser("billing-admin-filter-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "116관리목록캠");
		PaymentAccountResult firstPenalty = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "이전 벌금 계좌", "하나은행", "116-PENALTY-1", "이전회계", null
		));
		PaymentAccountResult activePenalty = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "현재 벌금 계좌", "국민은행", "116-PENALTY-2", "현재회계", null
		));
		PaymentAccountResult coffee = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.COFFEE, "커피 계좌", "신한은행", "116-COFFEE-1", "커피회계", manager.id()
		));
		billingService.deletePaymentAccount(campus.campusId(), firstPenalty.id(), manager.id());

		List<PaymentAccountResult> defaultResults = billingService.listAdminPaymentAccounts(
			campus.campusId(),
			manager.id(),
			null,
			false
		);
		List<PaymentAccountResult> penaltyResults = billingService.listAdminPaymentAccounts(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			true
		);

		assertThat(defaultResults)
			.extracting(PaymentAccountResult::id)
			.containsExactly(activePenalty.id(), coffee.id());
		assertThat(penaltyResults)
			.extracting(PaymentAccountResult::id)
			.containsExactly(activePenalty.id());
		assertThat(paymentAccountRepository.getReferenceById(firstPenalty.id()))
			.satisfies(deleted -> assertThat(deleted.deletedAt()).isNotNull());
	}

	@Test
	void activatePenaltyPaymentAccount_switches_active_account_and_is_idempotent_for_already_active_account() {
		User manager = saveUser("billing-activate-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "116활성화캠");
		PaymentAccountResult first = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "이전 벌금 계좌", "하나은행", "116-ACTIVE-1", "이전회계", null
		));
		PaymentAccountResult second = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "현재 벌금 계좌", "국민은행", "116-ACTIVE-2", "현재회계", null
		));

		PaymentAccountResult activated = billingService.activatePenaltyPaymentAccount(campus.campusId(), first.id(), manager.id());
		PaymentAccountResult idempotent = billingService.activatePenaltyPaymentAccount(campus.campusId(), first.id(), manager.id());

		assertThat(activated.id()).isEqualTo(first.id());
		assertThat(activated.isActive()).isTrue();
		assertThat(idempotent.id()).isEqualTo(first.id());
		assertThat(idempotent.isActive()).isTrue();
		assertThat(paymentAccountRepository.getReferenceById(first.id())).satisfies(account -> {
			assertThat(account.isActive()).isTrue();
			assertThat(account.deactivatedAt()).isNull();
		});
		assertThat(paymentAccountRepository.getReferenceById(second.id())).satisfies(account -> {
			assertThat(account.isActive()).isFalse();
			assertThat(account.deactivatedAt()).isNotNull();
		});
	}

	@Test
	void activatePenaltyPaymentAccount_rejects_deleted_mismatched_campus_and_coffee_accounts() {
		User manager = saveUser("billing-activate-invalid-manager@example.com", UserRole.MANAGER);
		CampusCreateResult campus = createCampus(manager, "116활성화오류캠");
		CampusCreateResult otherCampus = createCampus(manager, "116다른활성화오류캠");
		PaymentAccountResult penalty = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "벌금 계좌", "하나은행", "116-ACTIVATE-DEL", "벌금회계", null
		));
		PaymentAccountResult otherPenalty = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			otherCampus.campusId(), manager.id(), PaymentCategory.PENALTY, "다른 벌금 계좌", "하나은행", "116-ACTIVATE-OTHER", "벌금회계", null
		));
		PaymentAccountResult coffee = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.COFFEE, "커피 계좌", "신한은행", "116-ACTIVATE-COFFEE", "커피회계", manager.id()
		));
		billingService.deactivatePaymentAccount(coffee.id(), manager.id());
		billingService.deactivatePaymentAccount(penalty.id(), manager.id());
		billingService.deletePaymentAccount(campus.campusId(), penalty.id(), manager.id());

		assertThatThrownBy(() -> billingService.activatePenaltyPaymentAccount(campus.campusId(), penalty.id(), manager.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("납부 계좌를 찾을 수 없습니다.");
		assertThatThrownBy(() -> billingService.activatePenaltyPaymentAccount(campus.campusId(), otherPenalty.id(), manager.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("납부 계좌를 찾을 수 없습니다.");
		assertThatThrownBy(() -> billingService.activatePenaltyPaymentAccount(campus.campusId(), coffee.id(), manager.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("PENALTY 계좌만 활성화할 수 있습니다.");
	}

	@Test
	void deletePaymentAccount_allows_only_inactive_accounts_and_preserves_charge_snapshots() {
		User manager = saveUser("billing-delete-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-delete-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "116삭제캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult first = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "이전 벌금 계좌", "하나은행", "116-DELETE-1", "이전회계", null
		));
		ChargeItem charge = saveCharge(campus.campusId(), member.id(), first, 11601L);
		charge.markPaid();
		chargeItemRepository.saveAndFlush(charge);
		PaymentAccountResult active = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(), manager.id(), PaymentCategory.PENALTY, "현재 벌금 계좌", "국민은행", "116-DELETE-2", "현재회계", null
		));

		assertThatThrownBy(() -> billingService.deletePaymentAccount(campus.campusId(), active.id(), manager.id()))
			.isInstanceOf(BusinessException.class)
			.hasMessage("활성 계좌는 삭제할 수 없습니다.");

		billingService.deletePaymentAccount(campus.campusId(), first.id(), manager.id());

		assertThat(paymentAccountRepository.getReferenceById(first.id())).satisfies(account -> {
			assertThat(account.isActive()).isFalse();
			assertThat(account.deletedAt()).isNotNull();
		});
		assertThat(chargeItemRepository.findById(charge.id())).get().satisfies(saved -> {
			assertThat(saved.paymentAccountId()).isEqualTo(first.id());
			assertThat(saved.bankNameSnapshot()).isEqualTo("하나은행");
			assertThat(saved.accountNumberSnapshot()).isEqualTo("116-DELETE-1");
			assertThat(saved.accountHolderSnapshot()).isEqualTo("이전회계");
		});
	}

	@Test
	void replacingActiveAccount_relinks_unpaid_charges_and_preserves_terminal_snapshots() {
		User manager = saveUser("billing-relink-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-relink-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "43캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult oldAccount = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"기존 벌금 계좌",
			"카카오뱅크",
			"3333-00-4444444",
			"기존회계",
			null
		));
		ChargeItem unpaid = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			oldAccount.id(),
			"카카오뱅크",
			"3333-00-4444444",
			"기존회계",
			ChargeSourceType.DEVOTION_RECORD,
			1001L,
			"벌금",
			"경건생활",
			3000,
			LocalDate.now()
		));
		ChargeItem paid = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			oldAccount.id(),
			"카카오뱅크",
			"3333-00-4444444",
			"기존회계",
			ChargeSourceType.DEVOTION_RECORD,
			1002L,
			"벌금",
			"경건생활",
			3000,
			LocalDate.now()
		));
		paid.markPaid();
		ChargeItem waived = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			oldAccount.id(),
			"카카오뱅크",
			"3333-00-4444444",
			"기존회계",
			ChargeSourceType.DEVOTION_RECORD,
			1003L,
			"벌금",
			"경건생활",
			3000,
			LocalDate.now()
		));
		waived.markWaived();
		ChargeItem canceled = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			oldAccount.id(),
			"카카오뱅크",
			"3333-00-4444444",
			"기존회계",
			ChargeSourceType.DEVOTION_RECORD,
			1004L,
			"벌금",
			"경건생활",
			3000,
			LocalDate.now()
		));
		canceled.markCanceled();

		PaymentAccountResult newAccount = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"새 벌금 계좌",
			"우리은행",
			"1002-444-555555",
			"새회계",
			null
		));

		assertThat(chargeItemRepository.findById(unpaid.id())).get().satisfies(charge -> {
			assertThat(charge.paymentAccountId()).isEqualTo(newAccount.id());
			assertThat(charge.bankNameSnapshot()).isEqualTo("우리은행");
			assertThat(charge.accountNumberSnapshot()).isEqualTo("1002-444-555555");
			assertThat(charge.accountHolderSnapshot()).isEqualTo("새회계");
		});
		assertThat(chargeItemRepository.findById(paid.id())).get().satisfies(charge -> {
			assertThat(charge.paymentAccountId()).isEqualTo(oldAccount.id());
			assertThat(charge.bankNameSnapshot()).isEqualTo("카카오뱅크");
			assertThat(charge.accountNumberSnapshot()).isEqualTo("3333-00-4444444");
			assertThat(charge.accountHolderSnapshot()).isEqualTo("기존회계");
		});
		assertThat(chargeItemRepository.findById(waived.id())).get().satisfies(charge -> {
			assertThat(charge.paymentAccountId()).isEqualTo(oldAccount.id());
			assertThat(charge.bankNameSnapshot()).isEqualTo("카카오뱅크");
			assertThat(charge.accountNumberSnapshot()).isEqualTo("3333-00-4444444");
			assertThat(charge.accountHolderSnapshot()).isEqualTo("기존회계");
		});
		assertThat(chargeItemRepository.findById(canceled.id())).get().satisfies(charge -> {
			assertThat(charge.paymentAccountId()).isEqualTo(oldAccount.id());
			assertThat(charge.bankNameSnapshot()).isEqualTo("카카오뱅크");
			assertThat(charge.accountNumberSnapshot()).isEqualTo("3333-00-4444444");
			assertThat(charge.accountHolderSnapshot()).isEqualTo("기존회계");
		});
	}

	@Test
	void createPenaltyCharge_saves_account_snapshot_and_fails_without_active_account_without_inserting_row() {
		User manager = saveUser("billing-charge-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-charge-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "44캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));

		assertThatThrownBy(() -> billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campus.campusId(),
			member.id(),
			ChargeSourceType.DEVOTION_RECORD,
			2001L,
			"경건생활 벌금",
			"2026-06-15 주간",
			2500,
			LocalDate.now()
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("관리자에게 문의하세요");
		assertThat(chargeItemRepository.count()).isZero();

		PaymentAccountResult account = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"하나은행",
			"123-456789-001",
			"벌금회계",
			null
		));

		ChargeItemResult charge = billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campus.campusId(),
			member.id(),
			ChargeSourceType.DEVOTION_RECORD,
			2001L,
			"경건생활 벌금",
			"2026-06-15 주간",
			2500,
			LocalDate.now()
		));

		assertThat(charge.paymentAccountId()).isEqualTo(account.id());
		assertThat(charge.bankNameSnapshot()).isEqualTo("하나은행");
		assertThat(charge.accountNumberSnapshot()).isEqualTo("123-456789-001");
		assertThat(charge.accountHolderSnapshot()).isEqualTo("벌금회계");
	}

	@Test
	void createPenaltyCharge_updates_existing_unpaid_charge_for_same_source_without_creating_duplicate_row() {
		User manager = saveUser("billing-upsert-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-upsert-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "46캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"하나은행",
			"123-456789-003",
			"벌금회계",
			null
		));
		LocalDate firstDueDate = LocalDate.of(2026, 6, 22);
		LocalDate secondDueDate = LocalDate.of(2026, 6, 29);

		ChargeItemResult first = billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campus.campusId(),
			member.id(),
			ChargeSourceType.DEVOTION_RECORD,
			4001L,
			"이전 벌금",
			"이전 사유",
			2500,
			firstDueDate
		));
		ChargeItemResult second = billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campus.campusId(),
			member.id(),
			ChargeSourceType.DEVOTION_RECORD,
			4001L,
			"수정 벌금",
			"수정 사유",
			3500,
			secondDueDate
		));

		assertThat(second.id()).isEqualTo(first.id());
		assertThat(chargeItemRepository.count()).isEqualTo(1);
		assertThat(second.title()).isEqualTo("수정 벌금");
		assertThat(second.reason()).isEqualTo("수정 사유");
		assertThat(second.amount()).isEqualTo(3500);
		assertThat(second.dueDate()).isEqualTo(secondDueDate);
		assertThat(second.bankNameSnapshot()).isEqualTo("하나은행");
		assertThat(second.accountNumberSnapshot()).isEqualTo("123-456789-003");
		assertThat(second.accountHolderSnapshot()).isEqualTo("벌금회계");
	}

	@Test
	void createPenaltyCharge_updates_existing_unpaid_charge_with_latest_active_penalty_account_snapshot() {
		User manager = saveUser("billing-upsert-account-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-upsert-account-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "47캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult firstAccount = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"기존 벌금 계좌",
			"하나은행",
			"123-456789-004",
			"기존회계",
			null
		));
		ChargeItemResult first = billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campus.campusId(),
			member.id(),
			ChargeSourceType.DEVOTION_RECORD,
			4002L,
			"경건생활 벌금",
			"이전 주간",
			2500,
			LocalDate.of(2026, 6, 22)
		));
		assertThat(first.paymentAccountId()).isEqualTo(firstAccount.id());

		PaymentAccountResult secondAccount = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"새 벌금 계좌",
			"우리은행",
			"1002-444-666666",
			"새회계",
			null
		));
		ChargeItemResult updated = billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campus.campusId(),
			member.id(),
			ChargeSourceType.DEVOTION_RECORD,
			4002L,
			"경건생활 벌금 수정",
			"수정 주간",
			4500,
			LocalDate.of(2026, 6, 29)
		));

		assertThat(updated.id()).isEqualTo(first.id());
		assertThat(chargeItemRepository.count()).isEqualTo(1);
		assertThat(updated.paymentAccountId()).isEqualTo(secondAccount.id());
		assertThat(updated.bankNameSnapshot()).isEqualTo("우리은행");
		assertThat(updated.accountNumberSnapshot()).isEqualTo("1002-444-666666");
		assertThat(updated.accountHolderSnapshot()).isEqualTo("새회계");
		assertThat(updated.title()).isEqualTo("경건생활 벌금 수정");
		assertThat(updated.reason()).isEqualTo("수정 주간");
		assertThat(updated.amount()).isEqualTo(4500);
		assertThat(updated.dueDate()).isEqualTo(LocalDate.of(2026, 6, 29));
	}

	@Test
	void createPenaltyCharge_rejects_existing_terminal_charge_without_overwriting_it() {
		User manager = saveUser("billing-terminal-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-terminal-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "48캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"하나은행",
			"123-456789-005",
			"벌금회계",
			null
		));
		ChargeItemResult paidCharge = billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campus.campusId(),
			member.id(),
			ChargeSourceType.DEVOTION_RECORD,
			4003L,
			"경건생활 벌금",
			"이전 주간",
			2500,
			LocalDate.of(2026, 6, 22)
		));
		ChargeItem chargeItem = chargeItemRepository.findById(paidCharge.id()).orElseThrow();
		chargeItem.markPaid();
		chargeItemRepository.saveAndFlush(chargeItem);

		assertThatThrownBy(() -> billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campus.campusId(),
			member.id(),
			ChargeSourceType.DEVOTION_RECORD,
			4003L,
			"덮어쓰기 시도",
			"수정 주간",
			4500,
			LocalDate.of(2026, 6, 29)
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("이미 종료된 청구는 갱신할 수 없습니다.");
		assertThat(chargeItemRepository.count()).isEqualTo(1);
		assertThat(chargeItemRepository.findById(paidCharge.id())).get().satisfies(charge -> {
			assertThat(charge.paymentAccountId()).isEqualTo(account.id());
			assertThat(charge.title()).isEqualTo("경건생활 벌금");
			assertThat(charge.reason()).isEqualTo("이전 주간");
			assertThat(charge.amount()).isEqualTo(2500);
			assertThat(charge.dueDate()).isEqualTo(LocalDate.of(2026, 6, 22));
		});
	}

	@Test
	void createOrUpdateCoffeeCharge_updates_unpaid_charge_and_preserves_terminal_charge() {
		User manager = saveUser("billing-coffee-upsert-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-coffee-upsert-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "커피청구갱신캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.COFFEE,
			"커피 계좌",
			"신한은행",
			"148-COFFEE-ACCOUNT",
			"커피회계",
			null
		));
		CreateCoffeeChargeCommand firstCommand = new CreateCoffeeChargeCommand(
			campus.campusId(),
			member.id(),
			account.id(),
			14801L,
			"아이스 아메리카노",
			"첫 정산",
			2000,
			null
		);
		ChargeItemResult first = billingService.createOrUpdateCoffeeCharge(firstCommand);

		ChargeItemResult updated = billingService.createOrUpdateCoffeeCharge(new CreateCoffeeChargeCommand(
			campus.campusId(),
			member.id(),
			account.id(),
			14801L,
			"카페 라떼",
			"최종 응답 정산",
			3500,
			null
		));

		assertThat(updated.id()).isEqualTo(first.id());
		assertThat(updated.title()).isEqualTo("카페 라떼");
		assertThat(updated.reason()).isEqualTo("최종 응답 정산");
		assertThat(updated.amount()).isEqualTo(3500);
		assertThat(updated.paymentAccountId()).isEqualTo(account.id());
		assertThat(updated.accountNumberSnapshot()).isEqualTo("148-COFFEE-ACCOUNT");
		assertThat(chargeItemRepository.count()).isEqualTo(1);

		Instant paidAt = Instant.parse("2026-07-10T01:00:00Z");
		ChargeItem terminal = chargeItemRepository.findById(updated.id()).orElseThrow();
		terminal.markPaid(paidAt);
		chargeItemRepository.saveAndFlush(terminal);

		ChargeItemResult preserved = billingService.createOrUpdateCoffeeCharge(new CreateCoffeeChargeCommand(
			campus.campusId(),
			member.id(),
			account.id(),
			14801L,
			"덮어쓰기 시도",
			"종료 후 재정산",
			5000,
			null
		));

		assertThat(preserved.id()).isEqualTo(first.id());
		assertThat(preserved.status()).isEqualTo(ChargeStatus.PAID);
		assertThat(preserved.paidAt()).isEqualTo(paidAt);
		assertThat(preserved.title()).isEqualTo("카페 라떼");
		assertThat(preserved.reason()).isEqualTo("최종 응답 정산");
		assertThat(preserved.amount()).isEqualTo(3500);
		assertThat(preserved.accountNumberSnapshot()).isEqualTo("148-COFFEE-ACCOUNT");
		assertThat(chargeItemRepository.count()).isEqualTo(1);
	}

	@Test
	void completeMyChargePayment_marks_only_own_unpaid_charge_paid_with_requested_paid_at() {
		User manager = saveUser("billing-paid-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-paid-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "49캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-006");
		ChargeItem charge = saveCharge(campus.campusId(), member.id(), account, 5001L);
		Instant paidAt = Instant.parse("2026-06-12T12:30:00Z");

		ChargeItemResult result = billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
			campus.campusId(),
			charge.id(),
			member.id(),
			paidAt
		));

		assertThat(result.status()).isEqualTo(ChargeStatus.PAID);
		assertThat(result.paidAt()).isEqualTo(paidAt);
		assertThat(chargeItemRepository.findById(charge.id())).get().satisfies(saved -> {
			assertThat(saved.status()).isEqualTo(ChargeStatus.PAID);
			assertThat(saved.paidAt()).isEqualTo(paidAt);
		});
	}

	@Test
	void completeMyChargePayment_uses_server_time_when_paid_at_is_omitted() {
		User manager = saveUser("billing-paid-now-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-paid-now-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "50캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-007");
		ChargeItem charge = saveCharge(campus.campusId(), member.id(), account, 5002L);
		Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);

		ChargeItemResult result = billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
			campus.campusId(),
			charge.id(),
			member.id(),
			null
		));

		Instant after = Instant.now().plus(1, ChronoUnit.SECONDS);
		assertThat(result.status()).isEqualTo(ChargeStatus.PAID);
		assertThat(result.paidAt()).isBetween(before, after);
	}

	@Test
	void completeMyChargePayment_rejects_other_user_other_campus_and_terminal_statuses() {
		User manager = saveUser("billing-paid-auth-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-paid-auth-member@example.com", UserRole.USER);
		User otherMember = saveUser("billing-paid-auth-other@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "51캠");
		CampusCreateResult otherCampus = createCampus(manager, "52캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(otherMember.id(), campus.inviteCode()));
		PaymentAccountResult account = createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-008");
		ChargeItem charge = saveCharge(campus.campusId(), member.id(), account, 5003L);
		ChargeItem paid = saveCharge(campus.campusId(), member.id(), account, 5004L);
		paid.markPaid(Instant.parse("2026-06-12T12:30:00Z"));
		ChargeItem waived = saveCharge(campus.campusId(), member.id(), account, 5005L);
		waived.waive();
		ChargeItem canceled = saveCharge(campus.campusId(), member.id(), account, 5006L);
		canceled.cancel();

		assertThatThrownBy(() -> billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
			campus.campusId(),
			charge.id(),
			otherMember.id(),
			null
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("본인 청구 항목만 납부 완료 처리할 수 있습니다.");
		assertThatThrownBy(() -> billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
			otherCampus.campusId(),
			charge.id(),
			member.id(),
			null
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("청구 항목의 캠퍼스가 요청 캠퍼스와 일치하지 않습니다.");
		assertThatThrownBy(() -> billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
			campus.campusId(),
			paid.id(),
			member.id(),
			null
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("미납 상태의 청구만 납부 완료 처리할 수 있습니다.");
		assertThatThrownBy(() -> billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
			campus.campusId(),
			waived.id(),
			member.id(),
			null
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("미납 상태의 청구만 납부 완료 처리할 수 있습니다.");
		assertThatThrownBy(() -> billingService.completeMyChargePayment(new CompleteChargePaymentCommand(
			campus.campusId(),
			canceled.id(),
			member.id(),
			null
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("미납 상태의 청구만 납부 완료 처리할 수 있습니다.");
	}

	@Test
	void changeChargeStatus_allows_admin_waive_cancel_and_reopen_with_paid_at_cleared() {
		User manager = saveUser("billing-status-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-status-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "53캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-009");
		ChargeItem waiveTarget = saveCharge(campus.campusId(), member.id(), account, 5007L);
		ChargeItem cancelTarget = saveCharge(
			campus.campusId(), member.id(), account, ChargeSourceType.POLL_RESPONSE, 5008L
		);
		ChargeItem paidTarget = saveCharge(campus.campusId(), member.id(), account, 5009L);
		paidTarget.markPaid(Instant.parse("2026-06-12T12:30:00Z"));

		ChargeItemResult waived = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			waiveTarget.id(),
			manager.id(),
			ChargeStatus.WAIVED
		));
		ChargeItemResult canceled = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			cancelTarget.id(),
			manager.id(),
			ChargeStatus.CANCELED
		));
		ChargeItemResult reopened = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			paidTarget.id(),
			manager.id(),
			ChargeStatus.UNPAID
		));

		assertThat(waived.status()).isEqualTo(ChargeStatus.WAIVED);
		assertThat(canceled.status()).isEqualTo(ChargeStatus.CANCELED);
		assertThat(reopened.status()).isEqualTo(ChargeStatus.UNPAID);
		assertThat(reopened.paidAt()).isNull();
		assertThat(chargeItemRepository.findById(paidTarget.id())).get().satisfies(saved -> {
			assertThat(saved.status()).isEqualTo(ChargeStatus.UNPAID);
			assertThat(saved.paidAt()).isNull();
		});
	}

	@Test
	void changeChargeStatus_allows_service_admin_to_mark_paid_without_campus_membership() {
		User manager = saveUser("billing-status-service-admin-manager@example.com", UserRole.MANAGER);
		User serviceAdmin = saveUser("billing-status-service-admin@example.com", UserRole.ADMIN);
		User member = saveUser("billing-status-service-admin-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "57캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-014");
		ChargeItem charge = saveCharge(campus.campusId(), member.id(), account, 5012L);

		ChargeItemResult result = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			charge.id(),
			serviceAdmin.id(),
			ChargeStatus.PAID
		));

		assertThat(result.status()).isEqualTo(ChargeStatus.PAID);
		assertThat(result.paidAt()).isNotNull();
	}

	@Test
	void changeChargeStatus_allows_elder_and_campus_leader_to_mark_paid() {
		User manager = saveUser("billing-status-campus-admin-manager@example.com", UserRole.MANAGER);
		User elder = saveUser("billing-status-elder@example.com", UserRole.USER);
		User campusLeader = saveUser("billing-status-campus-leader@example.com", UserRole.USER);
		User member = saveUser("billing-status-campus-admin-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "58캠");
		campusService.joinCampus(new JoinCampusCommand(elder.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(campusLeader.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		CampusMember elderMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), elder.id())
			.orElseThrow();
		CampusMember leaderMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), campusLeader.id())
			.orElseThrow();
		ReflectionTestUtils.setField(elderMembership, "campusRole", CampusRole.ELDER);
		ReflectionTestUtils.setField(leaderMembership, "campusRole", CampusRole.CAMPUS_LEADER);
		campusMemberRepository.saveAndFlush(elderMembership);
		campusMemberRepository.saveAndFlush(leaderMembership);
		PaymentAccountResult account = createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-015");
		ChargeItem elderPaidTarget = saveCharge(campus.campusId(), member.id(), account, 5013L);
		ChargeItem leaderPaidTarget = saveCharge(
			campus.campusId(), member.id(), account, ChargeSourceType.POLL_RESPONSE, 5014L
		);

		ChargeItemResult elderPaid = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			elderPaidTarget.id(),
			elder.id(),
			ChargeStatus.PAID
		));
		ChargeItemResult leaderPaid = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			leaderPaidTarget.id(),
			campusLeader.id(),
			ChargeStatus.PAID
		));

		assertThat(elderPaid.status()).isEqualTo(ChargeStatus.PAID);
		assertThat(elderPaid.paidAt()).isNotNull();
		assertThat(leaderPaid.status()).isEqualTo(ChargeStatus.PAID);
		assertThat(leaderPaid.paidAt()).isNotNull();
	}

	@Test
	void changeChargeStatus_allows_paid_target_and_rejects_terminal_paid_normal_member_manager_without_membership() {
		User manager = saveUser("billing-status-auth-manager@example.com", UserRole.MANAGER);
		User serviceManager = saveUser("billing-status-auth-service-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-status-auth-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "54캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = createPenaltyAccount(campus.campusId(), manager.id(), "123-456789-010");
		ChargeItem charge = saveCharge(campus.campusId(), member.id(), account, 5010L);
		ChargeItem waived = saveCharge(campus.campusId(), member.id(), account, 5011L);
		waived.waive();

		ChargeItemResult paid = billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			charge.id(),
			manager.id(),
			ChargeStatus.PAID
		));
		assertThat(paid.status()).isEqualTo(ChargeStatus.PAID);
		assertThat(paid.paidAt()).isNotNull();
		assertThatThrownBy(() -> billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			waived.id(),
			manager.id(),
			ChargeStatus.PAID
		)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.BILLING_CHARGE_STATUS_TRANSITION_CONFLICT)
			)
			.hasMessage("허용되지 않는 청구 상태 전이입니다.");
		assertThatThrownBy(() -> billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			charge.id(),
			member.id(),
			ChargeStatus.WAIVED
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("청구 상태 변경 권한이 없습니다.");
		assertThatThrownBy(() -> billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			charge.id(),
			serviceManager.id(),
			ChargeStatus.WAIVED
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("청구 상태 변경 권한이 없습니다.");
		assertThatThrownBy(() -> billingService.changeChargeStatus(new ChangeChargeStatusCommand(
			waived.id(),
			manager.id(),
			ChargeStatus.CANCELED
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("허용되지 않는 청구 상태 전이입니다.");
	}

	@Test
	void duplicateCharge_is_prevented_by_unique_key() {
		User manager = saveUser("billing-unique-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-unique-member@example.com", UserRole.USER);
		CampusCreateResult campus = createCampus(manager, "45캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"하나은행",
			"123-456789-002",
			"벌금회계",
			null
		));
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			account.id(),
			"하나은행",
			"123-456789-002",
			"벌금회계",
			ChargeSourceType.DEVOTION_RECORD,
			3001L,
			"경건생활 벌금",
			"2026-06-15 주간",
			2500,
			LocalDate.now()
		));

		assertThatThrownBy(() -> chargeItemRepository.saveAndFlush(ChargeItem.create(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			account.id(),
			"하나은행",
			"123-456789-002",
			"벌금회계",
			ChargeSourceType.DEVOTION_RECORD,
			3001L,
			"경건생활 벌금",
			"2026-06-15 주간",
			2500,
			LocalDate.now()
		))).isInstanceOf(DataIntegrityViolationException.class);
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(manager.id(), name, "분당", "분당 " + name));
	}

	private PaymentAccountResult createPenaltyAccount(Long campusId, Long managerId, String accountNumber) {
		return billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			managerId,
			PaymentCategory.PENALTY,
			"벌금 계좌",
			"하나은행",
			accountNumber,
			"벌금회계",
			null
		));
	}

	private ChargeItem saveCharge(Long campusId, Long userId, PaymentAccountResult account, Long sourceId) {
		return saveCharge(campusId, userId, account, ChargeSourceType.DEVOTION_RECORD, sourceId);
	}

	private ChargeItem saveCharge(
		Long campusId,
		Long userId,
		PaymentAccountResult account,
		ChargeSourceType sourceType,
		Long sourceId
	) {
		return chargeItemRepository.saveAndFlush(ChargeItem.create(
			campusId,
			userId,
			PaymentCategory.PENALTY,
			account.id(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			sourceType,
			sourceId,
			"경건생활 벌금",
			"2026-06-15 주간",
			2500,
			LocalDate.of(2026, 6, 22)
		));
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.save(User.create("빌링테스트", email, "encoded"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
