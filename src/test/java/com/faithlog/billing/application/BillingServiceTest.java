package com.faithlog.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.PaymentAccount;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.billing.infrastructure.jpa.PaymentAccountRepository;
import com.faithlog.campus.application.CampusCreateResult;
import com.faithlog.campus.application.CampusService;
import com.faithlog.campus.application.CreateCampusCommand;
import com.faithlog.campus.application.JoinCampusCommand;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
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
	void listPaymentAccounts_allows_only_active_campus_members_and_returns_active_accounts() {
		User manager = saveUser("billing-list-manager@example.com", UserRole.MANAGER);
		User member = saveUser("billing-list-member@example.com", UserRole.USER);
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

	private User saveUser(String email, UserRole role) {
		User user = userRepository.save(User.create("빌링테스트", email, "encoded"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
