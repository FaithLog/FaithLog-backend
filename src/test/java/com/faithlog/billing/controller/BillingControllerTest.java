package com.faithlog.billing.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.billing.service.BillingService;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.service.command.CreatePenaltyChargeCommand;
import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.service.command.AssignCoffeeDutyCommand;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.ChangeCampusRoleCommand;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusRole;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BillingControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private BillingService billingService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Test
	void payment_account_api_maps_member_response_and_admin_permissions() throws Exception {
		String managerToken = signupAndLogin("billing-http-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "46캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());

		String createBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "46캠 벌금 계좌",
					  "bankName": "카카오뱅크",
					  "accountNumber": "3333-00-5555555",
					  "accountHolder": "회계",
					  "ownerUserId": null
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.id").isNumber())
			.andExpect(jsonPath("$.data.accountType").value("PENALTY"))
			.andExpect(jsonPath("$.data.accountNumber").value("3333-00-5555555"))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long accountId = objectMapper.readTree(createBody).path("data").path("id").asLong();

		mockMvc.perform(get("/api/v1/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(accountId))
			.andExpect(jsonPath("$.data[0].accountType").value("PENALTY"))
			.andExpect(jsonPath("$.data[0].nickname").value("46캠 벌금 계좌"))
			.andExpect(jsonPath("$.data[0].bankName").value("카카오뱅크"))
			.andExpect(jsonPath("$.data[0].accountNumber").value("3333-00-5555555"))
			.andExpect(jsonPath("$.data[0].accountHolder").value("회계"))
			.andExpect(jsonPath("$.data[0].ownerUserId").doesNotExist())
			.andExpect(jsonPath("$.data[0].isActive").doesNotExist())
			.andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
			.andExpect(jsonPath("$.data[0].deactivatedAt").doesNotExist());

		String adminToken = signupAndLogin("billing-http-admin@example.com", UserRole.ADMIN);
		mockMvc.perform(get("/api/v1/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(accountId))
			.andExpect(jsonPath("$.data[0].accountNumber").value("3333-00-5555555"))
			.andExpect(jsonPath("$.data[0].ownerUserId").doesNotExist())
			.andExpect(jsonPath("$.data[0].isActive").doesNotExist());

		mockMvc.perform(patch("/api/v1/admin/payment-accounts/{accountId}/deactivate", accountId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(accountId))
			.andExpect(jsonPath("$.data.isActive").value(false));
	}

	@Test
	void payment_account_api_rejects_non_member_inactive_member_and_normal_member_admin_actions() throws Exception {
		String managerToken = signupAndLogin("billing-http-auth-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "47캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-auth-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-auth-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		String outsiderToken = signupAndLogin("billing-http-auth-outsider@example.com", UserRole.USER);
		String inactiveToken = signupAndLogin("billing-http-auth-inactive@example.com", UserRole.USER);
		User inactive = userRepository.findByEmail("billing-http-auth-inactive@example.com").orElseThrow();
		joinCampus(inactiveToken, campus.path("inviteCode").asText());
		CampusMember inactiveMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, inactive.id()).orElseThrow();
		inactiveMembership.deactivate();
		campusMemberRepository.saveAndFlush(inactiveMembership);

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "47캠 벌금 계좌",
					  "bankName": "카카오뱅크",
					  "accountNumber": "3333-00-6666666",
					  "accountHolder": "회계"
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("납부 계좌 관리 권한이 없습니다."));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + outsiderToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("캠퍼스 납부 계좌 조회 권한이 없습니다."));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + inactiveToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("캠퍼스 납부 계좌 조회 권한이 없습니다."));

		CampusMember memberMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, member.id()).orElseThrow();
		ReflectionTestUtils.setField(memberMembership, "campusRole", CampusRole.CAMPUS_LEADER);
		campusMemberRepository.saveAndFlush(memberMembership);

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "47캠 벌금 계좌",
					  "bankName": "카카오뱅크",
					  "accountNumber": "3333-00-6666666",
					  "accountHolder": "회계"
					}
					"""))
			.andExpect(status().isCreated());
	}

	@Test
	void admin_payment_account_list_activate_and_delete_api_exposes_penalty_active_inactive_policy() throws Exception {
		String managerToken = signupAndLogin("billing-http-116-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-116-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "116HTTP캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-116-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-116-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		PaymentAccountResult first = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId, manager.id(), PaymentCategory.PENALTY, "이전 벌금 계좌", "하나은행", "116-HTTP-1", "이전회계", null
		));
		ChargeItem charge = chargeItemRepository.saveAndFlush(ChargeItem.create(
			campusId,
			member.id(),
			PaymentCategory.PENALTY,
			first.id(),
			first.bankName(),
			first.accountNumber(),
			first.accountHolder(),
			ChargeSourceType.DEVOTION_RECORD,
			11602L,
			"경건생활 벌금",
			"2026-07-01 주간",
			2500,
			LocalDate.of(2026, 7, 6)
		));
		charge.markPaid();
		chargeItemRepository.saveAndFlush(charge);
		PaymentAccountResult second = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId, manager.id(), PaymentCategory.PENALTY, "현재 벌금 계좌", "국민은행", "116-HTTP-2", "현재회계", null
		));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("accountType", "PENALTY"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(second.id()))
			.andExpect(jsonPath("$.data[0].isActive").value(true));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("accountType", "PENALTY")
				.param("includeInactive", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(first.id()))
			.andExpect(jsonPath("$.data[0].isActive").value(false))
			.andExpect(jsonPath("$.data[1].id").value(second.id()))
			.andExpect(jsonPath("$.data[1].isActive").value(true));

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate", campusId, first.id())
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(first.id()))
			.andExpect(jsonPath("$.data.isActive").value(true));

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate", campusId, first.id())
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(first.id()))
			.andExpect(jsonPath("$.data.isActive").value(true));

		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}", campusId, first.id())
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("BILLING_PAYMENT_ACCOUNT_ACTIVE_DELETE_FORBIDDEN"));

		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}", campusId, second.id())
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("accountType", "PENALTY")
				.param("includeInactive", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(first.id()));

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate", campusId, second.id())
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("BILLING_PAYMENT_ACCOUNT_NOT_FOUND"));

		assertThatChargeSnapshotPreserved(charge.id(), first.id(), "하나은행", "116-HTTP-1", "이전회계");
	}

	@Test
	void admin_payment_account_activate_rejects_coffee_account() throws Exception {
		String managerToken = signupAndLogin("billing-http-116-coffee-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-116-coffee-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "116커피활성화HTTP캠");
		long campusId = campus.path("campusId").asLong();
		PaymentAccountResult coffee = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId, manager.id(), PaymentCategory.COFFEE, "커피 계좌", "하나은행", "116-HTTP-COFFEE", "커피회계", manager.id()
		));
		billingService.deactivatePaymentAccount(coffee.id(), manager.id());

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/payment-accounts/{paymentAccountId}/activate", campusId, coffee.id())
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_PAYMENT_ACCOUNT_ACTIVATE_UNSUPPORTED"))
			.andExpect(jsonPath("$.message").value("PENALTY 계좌만 활성화할 수 있습니다."));
	}

	@Test
	void charge_status_api_completes_my_unpaid_charge_with_empty_body_and_changes_admin_status() throws Exception {
		String managerToken = signupAndLogin("billing-http-status-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-status-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "55캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-status-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-status-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-011");
		ChargeItemResult paidTarget = createPenaltyCharge(campusId, member.id(), 6001L);
		ChargeItemResult waiveTarget = createPenaltyCharge(campusId, member.id(), 6002L);

		mockMvc.perform(patch("/api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid", campusId, paidTarget.id())
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(paidTarget.id()))
			.andExpect(jsonPath("$.data.status").value("PAID"))
			.andExpect(jsonPath("$.data.paidAt").isNotEmpty());

		mockMvc.perform(patch("/api/v1/admin/charges/{chargeItemId}/status", waiveTarget.id())
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "WAIVED"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(waiveTarget.id()))
			.andExpect(jsonPath("$.data.status").value("WAIVED"))
			.andExpect(jsonPath("$.data.paidAt").doesNotExist());
	}

	@Test
	void charge_status_api_reopens_paid_charge_and_rejects_forbidden_or_invalid_transitions() throws Exception {
		String managerToken = signupAndLogin("billing-http-status-auth-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-status-auth-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "56캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-status-auth-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-status-auth-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-012");
		ChargeItemResult paidTarget = createPenaltyCharge(campusId, member.id(), 6003L);
		ChargeItemResult terminalTarget = createPenaltyCharge(campusId, member.id(), 6004L);
		ChargeItem paid = chargeItemRepository.findById(paidTarget.id()).orElseThrow();
		paid.markPaid();
		chargeItemRepository.saveAndFlush(paid);
		ChargeItem terminal = chargeItemRepository.findById(terminalTarget.id()).orElseThrow();
		terminal.markWaived();
		chargeItemRepository.saveAndFlush(terminal);

		mockMvc.perform(patch("/api/v1/admin/charges/{chargeItemId}/status", paidTarget.id())
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "UNPAID"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("UNPAID"))
			.andExpect(jsonPath("$.data.paidAt").doesNotExist());

		mockMvc.perform(patch("/api/v1/admin/charges/{chargeItemId}/status", terminalTarget.id())
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "PAID"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("관리자는 청구를 PAID로 변경할 수 없습니다."));

		mockMvc.perform(patch("/api/v1/admin/charges/{chargeItemId}/status", terminalTarget.id())
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "CANCELED"
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("청구 상태 변경 권한이 없습니다."));

		mockMvc.perform(patch("/api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid", campusId, terminalTarget.id())
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("미납 상태의 청구만 납부 완료 처리할 수 있습니다."));
	}

	@Test
	void charge_paid_api_rejects_inactive_member_even_for_own_charge() throws Exception {
		String managerToken = signupAndLogin("billing-http-paid-inactive-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-paid-inactive-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "57캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-paid-inactive-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-paid-inactive-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-016");
		ChargeItemResult charge = createPenaltyCharge(campusId, member.id(), 6005L);
		CampusMember membership = campusMemberRepository.findByCampusIdAndUserId(campusId, member.id()).orElseThrow();
		membership.deactivate();
		campusMemberRepository.saveAndFlush(membership);

		mockMvc.perform(patch("/api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid", campusId, charge.id())
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isForbidden());
	}

	@Test
	void charge_query_api_maps_my_summary_admin_campus_and_admin_member_responses() throws Exception {
		String managerToken = signupAndLogin("billing-http-query-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-query-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "58캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-query-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-query-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-017");
		ChargeItemResult unpaid = createPenaltyCharge(campusId, member.id(), 6101L);
		ChargeItemResult paidResult = createPenaltyCharge(campusId, member.id(), 6102L);
		ChargeItem paid = chargeItemRepository.findById(paidResult.id()).orElseThrow();
		paid.markPaid();
		chargeItemRepository.saveAndFlush(paid);

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("status", "UNPAID")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.campusId").value(campusId))
			.andExpect(jsonPath("$.data.summary.totalAmount").value(2500))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].id").value(unpaid.id()))
			.andExpect(jsonPath("$.data.items[0].account.paymentAccountId").isNumber())
			.andExpect(jsonPath("$.data.items[0].account.accountNumber").value("123-456789-017"))
			.andExpect(jsonPath("$.data.items[0].source.sourceType").value("DEVOTION_RECORD"))
			.andExpect(jsonPath("$.data.items[0].source.sourceId").value(6101));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me/summary", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("year", "2026")
				.param("month", "7"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.name").value("빌링테스트"))
			.andExpect(jsonPath("$.data.totalPaidAmount").value(2500))
			.andExpect(jsonPath("$.data.monthlyPaidAmount").value(2500))
			.andExpect(jsonPath("$.data.monthlyTotalChargeAmount").value(5000))
			.andExpect(jsonPath("$.data.monthlyByCategory[0].paymentCategory").value("PENALTY"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("keyword", "billing-http-query-member")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.totalAmount").value(5000))
			.andExpect(jsonPath("$.data.members.length()").value(1))
			.andExpect(jsonPath("$.data.members[0].userId").value(member.id()))
			.andExpect(jsonPath("$.data.members[0].email").value("billing-http-query-member@example.com"))
			.andExpect(jsonPath("$.data.members[0].items").doesNotExist());

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/members/{userId}/charges", campusId, member.id())
				.header("Authorization", "Bearer " + managerToken)
				.param("paymentCategory", "PENALTY")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andExpect(jsonPath("$.data.name").value("빌링테스트"))
			.andExpect(jsonPath("$.data.email").value("billing-http-query-member@example.com"))
			.andExpect(jsonPath("$.data.items.length()").value(2))
			.andExpect(jsonPath("$.data.items[0].account.accountNumber").value("123-456789-017"))
			.andExpect(jsonPath("$.data.items[0].source.sourceType").value("DEVOTION_RECORD"));
	}

	@Test
	void admin_campus_charge_query_with_unpaid_status_returns_only_members_having_unpaid_charges() throws Exception {
		String managerToken = signupAndLogin("billing-http-unpaid-filter-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-unpaid-filter-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "59캠");
		long campusId = campus.path("campusId").asLong();
		String unpaidMemberToken = signupAndLogin("billing-http-unpaid-filter-target@example.com", UserRole.USER);
		User unpaidMember = userRepository.findByEmail("billing-http-unpaid-filter-target@example.com").orElseThrow();
		joinCampus(unpaidMemberToken, campus.path("inviteCode").asText());
		String settledMemberToken = signupAndLogin("billing-http-unpaid-filter-settled@example.com", UserRole.USER);
		User settledMember = userRepository.findByEmail("billing-http-unpaid-filter-settled@example.com").orElseThrow();
		joinCampus(settledMemberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-019");
		ChargeItemResult unpaid = createPenaltyCharge(campusId, unpaidMember.id(), 6201L);
		ChargeItemResult paidResult = createPenaltyCharge(campusId, settledMember.id(), 6202L);
		ChargeItemResult waivedResult = createPenaltyCharge(campusId, settledMember.id(), 6203L);
		ChargeItemResult canceledResult = createPenaltyCharge(campusId, settledMember.id(), 6204L);
		ChargeItem paid = chargeItemRepository.findById(paidResult.id()).orElseThrow();
		paid.markPaid();
		ChargeItem waived = chargeItemRepository.findById(waivedResult.id()).orElseThrow();
		waived.markWaived();
		ChargeItem canceled = chargeItemRepository.findById(canceledResult.id()).orElseThrow();
		canceled.markCanceled();
		chargeItemRepository.saveAllAndFlush(List.of(paid, waived, canceled));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("status", "UNPAID")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.totalAmount").value(2500))
			.andExpect(jsonPath("$.data.summary.unpaidAmount").value(2500))
			.andExpect(jsonPath("$.data.summary.paidAmount").value(0))
			.andExpect(jsonPath("$.data.summary.waivedAmount").value(0))
			.andExpect(jsonPath("$.data.summary.canceledAmount").value(0))
			.andExpect(jsonPath("$.data.members.length()").value(1))
			.andExpect(jsonPath("$.data.members[0].userId").value(unpaidMember.id()))
			.andExpect(jsonPath("$.data.members[0].email").value("billing-http-unpaid-filter-target@example.com"))
			.andExpect(jsonPath("$.data.members[0].unpaidAmount").value(unpaid.amount()))
			.andExpect(jsonPath("$.data.members[0].paidAmount").value(0))
			.andExpect(jsonPath("$.data.members[0].waivedAmount").value(0))
			.andExpect(jsonPath("$.data.members[0].canceledAmount").value(0))
			.andExpect(jsonPath("$.data.members[0].items").doesNotExist());
	}

	@Test
	void admin_charge_query_supports_payment_account_scope_my_accounts_and_admin_account_list() throws Exception {
		String managerToken = signupAndLogin("billing-http-112-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-112-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "112캠");
		long campusId = campus.path("campusId").asLong();
		String dutyToken = signupAndLogin("billing-http-112-duty@example.com", UserRole.USER);
		User duty = userRepository.findByEmail("billing-http-112-duty@example.com").orElseThrow();
		joinCampus(dutyToken, campus.path("inviteCode").asText());
		String memberToken = signupAndLogin("billing-http-112-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-112-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campusId, manager.id(), duty.id()));
		long managerAccountId = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId, manager.id(), PaymentCategory.COFFEE, "관리자 커피 계좌", "하나은행", "112-HTTP-1", "관리회계", manager.id()
		)).id();
		long dutyAccountId = billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId, duty.id(), PaymentCategory.COFFEE, "담당자 커피 계좌", "하나은행", "112-HTTP-2", "담당회계", duty.id()
		)).id();
		billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId, manager.id(), PaymentCategory.PENALTY, "담당자 메타 벌금 계좌", "국민은행", "122-HTTP-PENALTY", "벌금회계", duty.id()
		));
		saveCoffeeCharge(campusId, member.id(), managerAccountId, 6501L, 2900);
		saveCoffeeCharge(campusId, member.id(), dutyAccountId, 6502L, 1800);
		createPenaltyCharge(campusId, member.id(), 6503L);

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("paymentAccountId", String.valueOf(managerAccountId))
				.param("paymentCategory", "COFFEE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.totalAmount").value(2900))
			.andExpect(jsonPath("$.data.members.length()").value(1));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + dutyToken)
				.param("paymentAccountId", String.valueOf(managerAccountId))
				.param("paymentCategory", "COFFEE"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BILLING_CHARGE_LIST_FORBIDDEN"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges/my-accounts", campusId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.totalAmount").value(5400))
			.andExpect(jsonPath("$.data.members[0].userId").value(member.id()))
			.andExpect(jsonPath("$.data.members[0].totalAmount").value(5400));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges/my-accounts", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("paymentCategory", "COFFEE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.totalAmount").value(2900))
			.andExpect(jsonPath("$.data.members[0].totalAmount").value(2900));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges/my-accounts", campusId)
				.header("Authorization", "Bearer " + dutyToken)
				.param("paymentCategory", "COFFEE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.totalAmount").value(1800))
			.andExpect(jsonPath("$.data.members[0].userId").value(member.id()));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(3))
			.andExpect(jsonPath("$.data[0].ownerUserId").value(manager.id()))
			.andExpect(jsonPath("$.data[0].isActive").value(true))
			.andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$.data[1].ownerUserId").value(duty.id()))
			.andExpect(jsonPath("$.data[1].isActive").value(true))
			.andExpect(jsonPath("$.data[2].accountType").value("PENALTY"))
			.andExpect(jsonPath("$.data[2].ownerUserId").value(duty.id()));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + dutyToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(dutyAccountId));
	}

	@Test
	void campus_role_admin_can_create_penalty_account_and_query_admin_charges_with_fresh_token() throws Exception {
		String managerToken = signupAndLogin("billing-http-119-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-119-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "119권한캠");
		long campusId = campus.path("campusId").asLong();
		String campusAdminTokenBeforeRoleChange = signupAndLogin("billing-http-119-campus-admin@example.com", UserRole.USER);
		User campusAdmin = userRepository.findByEmail("billing-http-119-campus-admin@example.com").orElseThrow();
		joinCampus(campusAdminTokenBeforeRoleChange, campus.path("inviteCode").asText());
		CampusMember campusAdminMembership = campusMemberRepository
			.findByCampusIdAndUserId(campusId, campusAdmin.id())
			.orElseThrow();
		campusService.changeCampusRole(new ChangeCampusRoleCommand(
			campusId,
			campusAdminMembership.id(),
			manager.id(),
			CampusRole.MINISTER
		));
		String campusAdminToken = login("billing-http-119-campus-admin@example.com");
		String memberToken = signupAndLogin("billing-http-119-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-119-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());

		String firstBody = createPenaltyAccountByHttp(campusId, campusAdminToken, "119-OLD");
		long firstAccountId = objectMapper.readTree(firstBody).path("data").path("id").asLong();
		createPenaltyCharge(campusId, member.id(), 11901L);

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + campusAdminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "119캠 새 벌금 계좌",
					  "bankName": "국민은행",
					  "accountNumber": "119-NEW",
					  "accountHolder": "회계"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.accountType").value("PENALTY"))
			.andExpect(jsonPath("$.data.accountNumber").value("119-NEW"))
			.andExpect(jsonPath("$.data.ownerUserId").hasJsonPath())
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andExpect(jsonPath("$.data.createdAt").isNotEmpty());

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + campusAdminToken)
				.param("accountType", "PENALTY")
				.param("includeInactive", "true"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(firstAccountId))
			.andExpect(jsonPath("$.data[0].isActive").value(false))
			.andExpect(jsonPath("$.data[1].accountNumber").value("119-NEW"))
			.andExpect(jsonPath("$.data[1].isActive").value(true));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + campusAdminToken)
				.param("status", "UNPAID")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.summary.unpaidAmount").value(2500))
			.andExpect(jsonPath("$.data.members[0].userId").value(member.id()));
	}

	@Test
	void billing_admin_apis_return_forbidden_for_member_and_unauthorized_without_token() throws Exception {
		String managerToken = signupAndLogin("billing-http-119-boundary-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-119-boundary-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "119경계캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-119-boundary-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-119-boundary-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "119-BOUNDARY");
		createPenaltyCharge(campusId, member.id(), 11902L);

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "권한 없는 벌금 계좌",
					  "bankName": "국민은행",
					  "accountNumber": "119-FORBIDDEN",
					  "accountHolder": "회계"
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BILLING_PAYMENT_ACCOUNT_MANAGE_FORBIDDEN"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("status", "UNPAID"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BILLING_CHARGE_LIST_FORBIDDEN"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges/my-accounts", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("status", "UNPAID"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BILLING_CHARGE_LIST_FORBIDDEN"));

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "무토큰 벌금 계좌",
					  "bankName": "국민은행",
					  "accountNumber": "119-UNAUTHORIZED",
					  "accountHolder": "회계"
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.param("status", "UNPAID"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges/my-accounts", campusId)
				.param("status", "UNPAID"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	@Test
	void charge_query_apis_reject_unsupported_sort_properties_and_directions() throws Exception {
		String managerToken = signupAndLogin("billing-http-admin-sort-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-admin-sort-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "60캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-admin-sort-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-admin-sort-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-020");
		createPenaltyCharge(campusId, member.id(), 6301L);

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("sort", "amount,asc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_SORT_PROPERTY"))
			.andExpect(jsonPath("$.message").value("지원하지 않는 정렬 기준입니다."));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("sort", "unpaidAmount,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.members.length()").value(1))
			.andExpect(jsonPath("$.data.members[0].userId").value(member.id()));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("sort", "unpaidAmount,wrong"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_SORT_DIRECTION"))
			.andExpect(jsonPath("$.message").value("지원하지 않는 정렬 방향입니다."));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("sort", "createdAt,ascending"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_SORT_DIRECTION"))
			.andExpect(jsonPath("$.message").value("지원하지 않는 정렬 방향입니다."));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("sort", "createdAt,desc,extra"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_SORT_FORMAT"))
			.andExpect(jsonPath("$.message").value("지원하지 않는 정렬 형식입니다."));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("sort", "unpaidAmount,asc,ignored"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_SORT_FORMAT"))
			.andExpect(jsonPath("$.message").value("지원하지 않는 정렬 형식입니다."));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("sort", "createdAt,asc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].id").isNumber());
	}

	@Test
	void charge_query_apis_reject_invalid_page_and_size_without_correction() throws Exception {
		String managerToken = signupAndLogin("billing-http-page-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("billing-http-page-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "61캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("billing-http-page-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("billing-http-page-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-021");
		createPenaltyCharge(campusId, member.id(), 6401L);

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("page", "-1"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_PAGE"))
			.andExpect(jsonPath("$.message").value("페이지 번호는 0 이상이어야 합니다."));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("size", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_SIZE"))
			.andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하이어야 합니다."));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("size", "101"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_SIZE"))
			.andExpect(jsonPath("$.message").value("페이지 크기는 1 이상 100 이하이어야 합니다."));
	}

	@Test
	void bean_validation_failure_returns_stable_validation_code() throws Exception {
		String managerToken = signupAndLogin("billing-http-validation-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "62캠");

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campus.path("campusId").asLong())
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "",
					  "bankName": "카카오뱅크",
					  "accountNumber": "3333-00-8888888",
					  "accountHolder": "회계"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("GLOBAL_VALIDATION_FAILED"))
			.andExpect(jsonPath("$.message").value("nickname: 공백일 수 없습니다"));
	}

	private JsonNode createCampus(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "region": "분당",
					  "description": "분당 %s퍼스"
					}
					""".formatted(name, name)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private void joinCampus(String accessToken, String inviteCode) throws Exception {
		mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(inviteCode)))
			.andExpect(status().isCreated());
	}

	private String createPenaltyAccountByHttp(Long campusId, String accessToken, String accountNumber) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "119캠 벌금 계좌",
					  "bankName": "하나은행",
					  "accountNumber": "%s",
					  "accountHolder": "회계"
					}
					""".formatted(accountNumber)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "빌링테스트",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

		User user = userRepository.findByEmail(email).orElseThrow();
		ReflectionTestUtils.setField(user, "role", role);
		userRepository.saveAndFlush(user);

		return login(email);
	}

	private String login(String email) throws Exception {
		String loginBody = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(loginBody).path("data").path("accessToken").asText();
	}

	private void createPenaltyAccount(Long campusId, Long managerId, String accountNumber) {
		billingService.createPaymentAccount(new CreatePaymentAccountCommand(
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

	private ChargeItemResult createPenaltyCharge(Long campusId, Long memberId, Long sourceId) {
		return billingService.createPenaltyCharge(new CreatePenaltyChargeCommand(
			campusId,
			memberId,
			ChargeSourceType.DEVOTION_RECORD,
			sourceId,
			"경건생활 벌금",
			"2026-06-15 주간",
			2500,
			LocalDate.of(2026, 6, 22)
		));
	}

	private void saveCoffeeCharge(Long campusId, Long memberId, Long accountId, Long sourceId, int amount) {
		chargeItemRepository.saveAndFlush(ChargeItem.create(
			campusId,
			memberId,
			PaymentCategory.COFFEE,
			accountId,
			"하나은행",
			"112-HTTP",
			"커피회계",
			ChargeSourceType.POLL_RESPONSE,
			sourceId,
			"커피 주문",
			"테스트",
			amount,
			null
		));
	}

	private void assertThatChargeSnapshotPreserved(
		Long chargeId,
		Long accountId,
		String bankName,
		String accountNumber,
		String accountHolder
	) {
		ChargeItem saved = chargeItemRepository.findById(chargeId).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(saved.paymentAccountId()).isEqualTo(accountId);
		org.assertj.core.api.Assertions.assertThat(saved.bankNameSnapshot()).isEqualTo(bankName);
		org.assertj.core.api.Assertions.assertThat(saved.accountNumberSnapshot()).isEqualTo(accountNumber);
		org.assertj.core.api.Assertions.assertThat(saved.accountHolderSnapshot()).isEqualTo(accountHolder);
	}
}
