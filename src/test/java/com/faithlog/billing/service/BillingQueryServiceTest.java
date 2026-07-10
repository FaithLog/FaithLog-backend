package com.faithlog.billing.service;

import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.query.AdminCampusChargeListQuery;
import com.faithlog.billing.service.query.AdminMemberChargeListQuery;
import com.faithlog.billing.service.query.MyChargeListQuery;
import com.faithlog.billing.service.query.MyChargeSummaryQuery;
import com.faithlog.billing.service.result.AdminCampusChargeMemberResult;
import com.faithlog.billing.service.result.AdminCampusChargesResult;
import com.faithlog.billing.service.result.AdminMemberChargesResult;
import com.faithlog.billing.service.result.ChargeCategorySummaryResult;
import com.faithlog.billing.service.result.MyChargeSummaryResult;
import com.faithlog.billing.service.result.MyChargesResult;
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
	private PaymentAccountRepository paymentAccountRepository;

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
	void campus_manager_my_accounts_includes_active_penalty_regardless_of_owner_and_only_owned_coffee_accounts() {
		User manager = saveUser("query-122-manager@example.com", UserRole.MANAGER, "관리자");
		User treasurer = saveUser("query-122-treasurer@example.com", UserRole.USER, "회계담당");
		User otherManager = saveUser("query-122-other-manager@example.com", UserRole.USER, "다른관리자");
		User member = saveUser("query-122-member@example.com", UserRole.USER, "멤버");
		CampusCreateResult campus = createCampus(manager, "122내계좌정책캠");
		campusService.joinCampus(new JoinCampusCommand(treasurer.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(otherManager.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		CampusMember otherManagerMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), otherManager.id())
			.orElseThrow();
		ReflectionTestUtils.setField(otherManagerMembership, "campusRole", CampusRole.ELDER);
		campusMemberRepository.saveAndFlush(otherManagerMembership);
		PaymentAccountResult penaltyAccount = createAccount(
			campus.campusId(),
			manager.id(),
			PaymentCategory.PENALTY,
			"122-PENALTY-OTHER-OWNER",
			treasurer.id()
		);
		PaymentAccountResult managerCoffeeAccount = createAccount(
			campus.campusId(),
			manager.id(),
			PaymentCategory.COFFEE,
			"122-COFFEE-MANAGER",
			manager.id()
		);
		PaymentAccountResult otherCoffeeAccount = createAccount(
			campus.campusId(),
			otherManager.id(),
			PaymentCategory.COFFEE,
			"122-COFFEE-OTHER",
			otherManager.id()
		);
		saveCharge(campus.campusId(), member.id(), penaltyAccount, PaymentCategory.PENALTY, ChargeSourceType.DEVOTION_RECORD,
			12201L, "다른 owner 벌금", 3000, ChargeStatus.UNPAID, LocalDate.of(2026, 7, 1));
		saveCharge(campus.campusId(), member.id(), managerCoffeeAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			12202L, "내 커피", 1800, ChargeStatus.UNPAID, null);
		saveCharge(campus.campusId(), member.id(), otherCoffeeAccount, PaymentCategory.COFFEE, ChargeSourceType.POLL_RESPONSE,
			12203L, "다른 owner 커피", 2900, ChargeStatus.UNPAID, null);

		AdminCampusChargesResult allMyAccounts = billingQueryService.listAdminCampusChargesForMyAccounts(
			new AdminCampusChargeListQuery(
				campus.campusId(),
				manager.id(),
				null,
				null,
				null,
				null,
				null,
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
			)
		);
		AdminCampusChargesResult coffeeOnly = billingQueryService.listAdminCampusChargesForMyAccounts(
			new AdminCampusChargeListQuery(
				campus.campusId(),
				manager.id(),
				PaymentCategory.COFFEE,
				null,
				null,
				null,
				null,
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
			)
		);

		assertThat(allMyAccounts.summary().totalAmount()).isEqualTo(4800);
		assertThat(allMyAccounts.members()).singleElement()
			.satisfies(memberResult -> {
				assertThat(memberResult.userId()).isEqualTo(member.id());
				assertThat(memberResult.totalAmount()).isEqualTo(4800);
			});
		assertThat(coffeeOnly.summary().totalAmount()).isEqualTo(1800);
		assertThat(coffeeOnly.members()).singleElement()
			.satisfies(memberResult -> assertThat(memberResult.totalAmount()).isEqualTo(1800));
	}

	@Test
	void campus_manager_and_service_admin_my_accounts_include_legacy_active_penalty_account_with_null_owner() {
		User manager = saveUser("query-122-null-owner-manager@example.com", UserRole.MANAGER, "관리자");
		User serviceAdmin = saveUser("query-122-null-owner-admin@example.com", UserRole.ADMIN, "서비스관리자");
		User member = saveUser("query-122-null-owner-member@example.com", UserRole.USER, "멤버");
		CampusCreateResult campus = createCampus(manager, "122nullOwner캠");
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		PaymentAccount legacyPenaltyAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.campusId(),
			PaymentCategory.PENALTY,
			"기존 null owner 벌금 계좌",
			"하나은행",
			"122-PENALTY-NULL",
			"기존회계",
			null
		));
		PaymentAccountResult legacyPenalty = PaymentAccountResult.from(legacyPenaltyAccount);
		saveCharge(campus.campusId(), member.id(), legacyPenalty, PaymentCategory.PENALTY, ChargeSourceType.DEVOTION_RECORD,
			12211L, "null owner 벌금", 2500, ChargeStatus.UNPAID, LocalDate.of(2026, 7, 1));

		AdminCampusChargesResult managerResult = billingQueryService.listAdminCampusChargesForMyAccounts(
			new AdminCampusChargeListQuery(
				campus.campusId(),
				manager.id(),
				PaymentCategory.PENALTY,
				null,
				null,
				null,
				null,
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
			)
		);
		AdminCampusChargesResult adminResult = billingQueryService.listAdminCampusChargesForMyAccounts(
			new AdminCampusChargeListQuery(
				campus.campusId(),
				serviceAdmin.id(),
				PaymentCategory.PENALTY,
				null,
				null,
				null,
				null,
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
			)
		);

		assertThat(managerResult.summary().totalAmount()).isEqualTo(2500);
		assertThat(managerResult.members()).singleElement()
			.satisfies(memberResult -> assertThat(memberResult.userId()).isEqualTo(member.id()));
		assertThat(adminResult.summary().totalAmount()).isEqualTo(2500);
		assertThat(adminResult.members()).singleElement()
			.satisfies(memberResult -> assertThat(memberResult.userId()).isEqualTo(member.id()));
	}

	@Test
	void coffee_duty_can_query_only_owned_active_coffee_account_charges_and_my_accounts() {
		User manager = saveUser("query-my-account-manager@example.com", UserRole.MANAGER, "관리자");
		User duty = saveUser("query-my-account-duty@example.com", UserRole.USER, "커피담당");
		User otherDuty = saveUser("query-my-account-other-duty@example.com", UserRole.USER, "다른담당");
		User member = saveUser("query-my-account-member@example.com", UserRole.USER, "멤버");
		User serviceAdmin = saveUser("query-my-account-admin@example.com", UserRole.ADMIN, "서비스관리자");
		CampusCreateResult campus = createCampus(manager, "112내계좌캠");
		campusService.joinCampus(new JoinCampusCommand(duty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(otherDuty.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(member.id(), campus.inviteCode()));
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campus.campusId(), manager.id(), duty.id()));
		CampusMember otherDutyMembership = campusMemberRepository.findByCampusIdAndUserId(campus.campusId(), otherDuty.id())
			.orElseThrow();
		ReflectionTestUtils.setField(otherDutyMembership, "campusRole", CampusRole.ELDER);
		campusMemberRepository.saveAndFlush(otherDutyMembership);
		PaymentAccountResult otherAccount = createAccount(campus.campusId(), otherDuty.id(), PaymentCategory.COFFEE, "112-102", otherDuty.id());
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
		assertThatThrownBy(() -> billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), manager.id(), PaymentCategory.COFFEE, null, null, null, otherAccount.id(),
			PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
		)))
			.isInstanceOf(BusinessException.class)
			.hasMessage("캠퍼스 청구 조회 권한이 없습니다.");
		assertThat(billingQueryService.listAdminCampusCharges(new AdminCampusChargeListQuery(
			campus.campusId(), serviceAdmin.id(), PaymentCategory.COFFEE, null, null, null, otherAccount.id(),
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
