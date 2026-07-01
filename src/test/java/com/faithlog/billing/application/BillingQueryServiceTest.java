package com.faithlog.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.application.AssignCoffeeDutyCommand;
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
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BillingQueryServiceTest {

	@Autowired
	private BillingQueryService billingQueryService;

	@Autowired
	private BillingService billingService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private UserRepository userRepository;

	@Test
	void listMyCharges_returns_summary_items_account_and_source_with_filters_and_paging() {
		User manager = saveUser("query-my-manager@example.com", UserRole.MANAGER, "관리자");
		User member = saveUser("query-my-member@example.com", UserRole.USER, "멤버");
		CampusCreateResult campus = createCampus(manager, "60캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult penaltyAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.PENALTY, "111-111");
		PaymentAccountResult coffeeAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.COFFEE, "222-222");
		ChargeItem unpaidPenalty = saveCharge(
			campus.campusId(), member.id(), penaltyAccount, PaymentCategory.PENALTY, ChargeSourceType.DEVOTION_RECORD,
			9001L, "경건생활 벌금", 3000, ChargeStatus.UNPAID, LocalDate.of(2026, 6, 22)
		);
		saveCharge(
			campus.campusId(), member.id(), coffeeAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			9002L, "커피 주문", 4500, ChargeStatus.PAID, LocalDate.of(2026, 6, 23)
		);

		MyChargesResult result = billingQueryService.listMyCharges(new MyChargeListQuery(
			campus.campusId(),
			member.id(),
			PaymentCategory.PENALTY,
			ChargeStatus.UNPAID,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		));

		assertThat(result.campusId()).isEqualTo(campus.campusId());
		assertThat(result.campusName()).isEqualTo("60캠");
		assertThat(result.region()).isEqualTo("분당");
		assertThat(result.summary().totalAmount()).isEqualTo(3000);
		assertThat(result.summary().unpaidAmount()).isEqualTo(3000);
		assertThat(result.summary().paidAmount()).isZero();
		assertThat(result.items()).hasSize(1);
		assertThat(result.items().getFirst()).satisfies(item -> {
			assertThat(item.id()).isEqualTo(unpaidPenalty.id());
			assertThat(item.paymentCategory()).isEqualTo(PaymentCategory.PENALTY);
			assertThat(item.dueDate()).isEqualTo(LocalDate.of(2026, 6, 22));
			assertThat(item.account().paymentAccountId()).isEqualTo(penaltyAccount.id());
			assertThat(item.account().bankName()).isEqualTo("하나은행");
			assertThat(item.account().accountNumber()).isEqualTo("111-111");
			assertThat(item.source().sourceType()).isEqualTo(ChargeSourceType.DEVOTION_RECORD);
			assertThat(item.source().sourceId()).isEqualTo(9001L);
		});
	}

	@Test
	void getMyChargeSummary_uses_paidAt_for_monthly_paid_and_createdAt_for_monthly_charge_totals() {
		User manager = saveUser("query-summary-manager@example.com", UserRole.MANAGER, "관리자");
		User member = saveUser("query-summary-member@example.com", UserRole.USER, "멤버");
		CampusCreateResult campus = createCampus(manager, "61캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult penaltyAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.PENALTY, "333-333");
		PaymentAccountResult coffeeAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.COFFEE, "444-444");
		ChargeItem junePaid = saveCharge(campus.campusId(), member.id(), penaltyAccount, PaymentCategory.PENALTY,
			ChargeSourceType.DEVOTION_RECORD, 9101L, "6월 벌금", 3000, ChargeStatus.UNPAID, LocalDate.of(2026, 6, 22));
		markCreatedAt(junePaid, "2026-06-10T00:00:00Z");
		junePaid.markPaid(Instant.parse("2026-06-20T12:00:00Z"));
		ChargeItem juneUnpaid = saveCharge(campus.campusId(), member.id(), coffeeAccount, PaymentCategory.COFFEE,
			ChargeSourceType.POLL_RESPONSE, 9102L, "6월 커피", 4500, ChargeStatus.UNPAID, LocalDate.of(2026, 6, 23));
		markCreatedAt(juneUnpaid, "2026-06-11T00:00:00Z");
		ChargeItem mayPaidInJune = saveCharge(campus.campusId(), member.id(), penaltyAccount, PaymentCategory.PENALTY,
			ChargeSourceType.DEVOTION_RECORD, 9103L, "5월 벌금", 2000, ChargeStatus.UNPAID, LocalDate.of(2026, 5, 31));
		markCreatedAt(mayPaidInJune, "2026-05-30T00:00:00Z");
		mayPaidInJune.markPaid(Instant.parse("2026-06-01T09:00:00Z"));

		MyChargeSummaryResult result = billingQueryService.getMyChargeSummary(new MyChargeSummaryQuery(
			campus.campusId(),
			member.id(),
			2026,
			6
		));

		assertThat(result.userId()).isEqualTo(member.id());
		assertThat(result.name()).isEqualTo("멤버");
		assertThat(result.totalPaidAmount()).isEqualTo(5000);
		assertThat(result.monthlyPaidAmount()).isEqualTo(5000);
		assertThat(result.monthlyUnpaidAmount()).isEqualTo(4500);
		assertThat(result.monthlyTotalChargeAmount()).isEqualTo(7500);
		assertThat(result.monthlyByCategory())
			.extracting(ChargeCategorySummaryResult::paymentCategory)
			.containsExactly(PaymentCategory.PENALTY, PaymentCategory.COFFEE);
	}

	@Test
	void listAdminCampusCharges_returns_members_summary_without_charge_items() {
		User manager = saveUser("query-admin-manager@example.com", UserRole.MANAGER, "관리자");
		User member = saveUser("query-admin-member@example.com", UserRole.USER, "멤버");
		User otherMember = saveUser("query-admin-other@example.com", UserRole.USER, "다른멤버");
		CampusCreateResult campus = createCampus(manager, "62캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(otherMember.id(), campus.inviteCode()));
		PaymentAccountResult penaltyAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.PENALTY, "555-555");
		saveCharge(campus.campusId(), member.id(), penaltyAccount, PaymentCategory.PENALTY, ChargeSourceType.DEVOTION_RECORD,
			9201L, "멤버 벌금", 3000, ChargeStatus.UNPAID, LocalDate.of(2026, 6, 22));
		saveCharge(campus.campusId(), otherMember.id(), penaltyAccount, PaymentCategory.PENALTY, ChargeSourceType.DEVOTION_RECORD,
			9202L, "다른 멤버 벌금", 2000, ChargeStatus.WAIVED, LocalDate.of(2026, 6, 22));

		AdminCampusChargesResult result = billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(),
			manager.id(),
			null,
			null,
			null,
			"멤버",
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		));

		assertThat(result.summary().totalAmount()).isEqualTo(5000);
		assertThat(result.summary().unpaidAmount()).isEqualTo(3000);
		assertThat(result.summary().waivedAmount()).isEqualTo(2000);
		assertThat(result.members()).hasSize(2);
		assertThat(result.members())
			.extracting(AdminCampusChargeMemberResult::userId)
			.containsExactlyInAnyOrder(member.id(), otherMember.id());
		assertThat(result.members())
			.filteredOn(memberResult -> memberResult.userId().equals(member.id()))
			.first()
			.satisfies(memberResult -> assertThat(memberResult.unpaidAmount()).isEqualTo(3000));
	}

	@Test
	void listAdminMemberCharges_includes_target_user_information_and_reuses_charge_item_shape() {
		User manager = saveUser("query-admin-detail-manager@example.com", UserRole.MANAGER, "관리자");
		User member = saveUser("query-admin-detail-member@example.com", UserRole.USER, "상세멤버");
		CampusCreateResult campus = createCampus(manager, "63캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccountResult account = createAccount(campus.campusId(), manager.id(), PaymentCategory.PENALTY, "666-666");
		ChargeItem charge = saveCharge(campus.campusId(), member.id(), account, PaymentCategory.PENALTY,
			ChargeSourceType.DEVOTION_RECORD, 9301L, "상세 벌금", 3000, ChargeStatus.UNPAID, LocalDate.of(2026, 6, 22));

		AdminMemberChargesResult result = billingQueryService.listAdminMemberCharges(new AdminMemberChargeListQuery(
			campus.campusId(),
			member.id(),
			manager.id(),
			null,
			null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		));

		assertThat(result.userId()).isEqualTo(member.id());
		assertThat(result.name()).isEqualTo("상세멤버");
		assertThat(result.email()).isEqualTo("query-admin-detail-member@example.com");
		assertThat(result.items()).hasSize(1);
		assertThat(result.items().getFirst().id()).isEqualTo(charge.id());
		assertThat(result.items().getFirst().account().accountNumber()).isEqualTo("666-666");
		assertThat(result.items().getFirst().source().sourceId()).isEqualTo(9301L);
	}

	@Test
	void charge_query_permissions_follow_member_and_admin_rules() {
		User manager = saveUser("query-auth-manager@example.com", UserRole.MANAGER, "관리자");
		User serviceManager = saveUser("query-auth-service-manager@example.com", UserRole.MANAGER, "서비스매니저");
		User member = saveUser("query-auth-member@example.com", UserRole.USER, "멤버");
		User admin = saveUser("query-auth-admin@example.com", UserRole.ADMIN, "서비스관리자");
		CampusCreateResult campus = createCampus(manager, "64캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));

		assertThatThrownBy(() -> billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), member.id(), null, null, null, null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 청구 조회 권한이 없습니다.");
		assertThatThrownBy(() -> billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), serviceManager.id(), null, null, null, null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 청구 조회 권한이 없습니다.");

		assertThat(billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), manager.id(), null, null, null, null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		))).isNotNull();
		assertThat(billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), admin.id(), null, null, null, null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		))).isNotNull();
	}

	@Test
	void coffee_duty_can_query_only_coffee_admin_charges_for_own_campus() {
		User manager = saveUser("query-coffee-duty-manager@example.com", UserRole.MANAGER, "관리자");
		User duty = saveUser("query-coffee-duty@example.com", UserRole.USER, "커피담당");
		User member = saveUser("query-coffee-duty-member@example.com", UserRole.USER, "멤버");
		CampusCreateResult campus = createCampus(manager, "65커피캠");
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		PaymentAccountResult coffeeAccount = createAccount(campus.campusId(), duty.id(), PaymentCategory.COFFEE, "777-777", duty.id());
		PaymentAccountResult penaltyAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.PENALTY, "888-888");
		saveCharge(campus.campusId(), member.id(), coffeeAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			9401L, "커피 주문", 4500, ChargeStatus.UNPAID, null);
		saveCharge(campus.campusId(), member.id(), penaltyAccount, PaymentCategory.PENALTY, ChargeSourceType.DEVOTION_RECORD,
			9402L, "경건생활 벌금", 3000, ChargeStatus.UNPAID, LocalDate.of(2026, 6, 22));

		AdminCampusChargesResult coffeeList = billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), duty.id(), PaymentCategory.COFFEE, null, null, null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		));
		AdminMemberChargesResult memberCoffeeList = billingQueryService.listAdminMemberCharges(new AdminMemberChargeListQuery(
			campus.campusId(), member.id(), duty.id(), PaymentCategory.COFFEE, null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		));

		assertThat(coffeeList.summary().totalAmount()).isEqualTo(4500);
		assertThat(coffeeList.members()).singleElement()
			.satisfies(result -> assertThat(result.userId()).isEqualTo(member.id()));
		assertThat(memberCoffeeList.items()).singleElement()
			.satisfies(item -> assertThat(item.paymentCategory()).isEqualTo(PaymentCategory.COFFEE));
		AdminCampusChargesResult scopedCoffeeList = billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), duty.id(), null, null, null, null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		));
		assertThat(scopedCoffeeList.summary().totalAmount()).isEqualTo(4500);
		assertThatThrownBy(() -> billingQueryService.listAdminMemberCharges(new AdminMemberChargeListQuery(
			campus.campusId(), member.id(), duty.id(), PaymentCategory.PENALTY, null,
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 청구 조회 권한이 없습니다.");
	}

	@Test
	void admin_campus_charges_can_filter_by_payment_account_with_existing_filters() {
		User manager = saveUser("query-account-filter-manager@example.com", UserRole.MANAGER, "관리자");
		User member = saveUser("query-account-filter-member@example.com", UserRole.USER, "계좌필터멤버");
		User otherMember = saveUser("query-account-filter-other@example.com", UserRole.USER, "다른멤버");
		CampusCreateResult campus = createCampus(manager, "112계좌필터캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(otherMember.id(), campus.inviteCode()));
		PaymentAccountResult firstCoffeeAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.COFFEE, "112-001");
		PaymentAccountResult secondCoffeeAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.COFFEE, "112-002");
		saveCharge(campus.campusId(), member.id(), firstCoffeeAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			11201L, "1번 계좌 커피", 1800, ChargeStatus.UNPAID, null);
		saveCharge(campus.campusId(), member.id(), secondCoffeeAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			11202L, "2번 계좌 커피", 2900, ChargeStatus.UNPAID, null);
		saveCharge(campus.campusId(), otherMember.id(), secondCoffeeAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			11203L, "다른 멤버 커피", 1500, ChargeStatus.PAID, null);

		AdminCampusChargesResult result = billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(),
			manager.id(),
			PaymentCategory.COFFEE,
			ChargeStatus.UNPAID,
			member.id(),
			"계좌필터",
			secondCoffeeAccount.id(),
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		));

		assertThat(result.summary().totalAmount()).isEqualTo(2900);
		assertThat(result.summary().unpaidAmount()).isEqualTo(2900);
		assertThat(result.members()).singleElement()
			.satisfies(memberResult -> {
				assertThat(memberResult.userId()).isEqualTo(member.id());
				assertThat(memberResult.totalAmount()).isEqualTo(2900);
			});
	}

	@Test
	void coffee_duty_can_query_only_owned_active_coffee_account_charges_and_my_accounts() {
		User manager = saveUser("query-my-account-manager@example.com", UserRole.MANAGER, "관리자");
		User duty = saveUser("query-my-account-duty@example.com", UserRole.USER, "커피담당");
		User otherDuty = saveUser("query-my-account-other-duty@example.com", UserRole.USER, "다른담당");
		User member = saveUser("query-my-account-member@example.com", UserRole.USER, "멤버");
		CampusCreateResult campus = createCampus(manager, "112내계좌캠");
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(otherDuty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		PaymentAccountResult otherAccount = createAccount(campus.campusId(), manager.id(), PaymentCategory.COFFEE, "112-102", otherDuty.id());
		PaymentAccountResult dutyAccount = createAccount(campus.campusId(), duty.id(), PaymentCategory.COFFEE, "112-101", duty.id());
		saveCharge(campus.campusId(), member.id(), dutyAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			11211L, "담당자 계좌 커피", 1800, ChargeStatus.UNPAID, null);
		saveCharge(campus.campusId(), member.id(), otherAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			11212L, "다른 계좌 커피", 2900, ChargeStatus.UNPAID, null);

		AdminCampusChargesResult myAccounts = billingQueryService.listAdminCampusChargesForMyAccounts(
			new AdminCampusChargeListQuery(
				campus.campusId(),
				duty.id(),
				PaymentCategory.COFFEE,
				null,
				null,
				null,
				null,
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
			)
		);

		assertThat(myAccounts.summary().totalAmount()).isEqualTo(1800);
		assertThat(myAccounts.members()).singleElement()
			.satisfies(memberResult -> assertThat(memberResult.totalAmount()).isEqualTo(1800));
		assertThatThrownBy(() -> billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), duty.id(), PaymentCategory.COFFEE, null, null, null, otherAccount.id(),
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 청구 조회 권한이 없습니다.");
		assertThat(billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), manager.id(), PaymentCategory.COFFEE, null, null, null, otherAccount.id(),
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		)).summary().totalAmount()).isEqualTo(2900);
	}

	private CampusCreateResult createCampus(User manager, String name) {
		return campusService.createCampus(new CreateCampusCommand(manager.id(), name, "분당", "분당 " + name));
	}

	private PaymentAccountResult createAccount(
		Long campusId,
		Long managerId,
		PaymentCategory accountType,
		String accountNumber
	) {
		return createAccount(campusId, managerId, accountType, accountNumber, null);
	}

	private PaymentAccountResult createAccount(
		Long campusId,
		Long requesterId,
		PaymentCategory accountType,
		String accountNumber,
		Long ownerUserId
	) {
		return billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			requesterId,
			accountType,
			accountType.name() + " 계좌",
			"하나은행",
			accountNumber,
			"회계",
			ownerUserId
		));
	}

	private ChargeItem saveCharge(
		Long campusId,
		Long userId,
		PaymentAccountResult account,
		PaymentCategory paymentCategory,
		ChargeSourceType sourceType,
		Long sourceId,
		String title,
		int amount,
		ChargeStatus status,
		LocalDate dueDate
	) {
		ChargeItem chargeItem = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campusId,
			userId,
			paymentCategory,
			account.id(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			sourceType,
			sourceId,
			title,
			"테스트 사유",
			amount,
			dueDate
		));
		if (status == ChargeStatus.PAID) {
			chargeItem.markPaid(Instant.parse("2026-06-20T12:00:00Z"));
		} else if (status == ChargeStatus.WAIVED) {
			chargeItem.waive();
		} else if (status == ChargeStatus.CANCELED) {
			chargeItem.cancel();
		}
		return chargeItemRepository.saveAndFlush(chargeItem);
	}

	private void markCreatedAt(ChargeItem chargeItem, String createdAt) {
		ReflectionTestUtils.setField(chargeItem, "createdAt", Instant.parse(createdAt));
		ReflectionTestUtils.setField(chargeItem, "updatedAt", Instant.parse(createdAt));
		chargeItemRepository.saveAndFlush(chargeItem);
	}

	private User saveUser(String email, UserRole role, String name) {
		User user = userRepository.save(User.create(name, email, "encoded"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
