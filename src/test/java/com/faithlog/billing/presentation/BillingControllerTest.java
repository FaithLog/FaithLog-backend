package com.faithlog.billing.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.ChargeItemResult;
import com.faithlog.billing.application.CreatePaymentAccountCommand;
import com.faithlog.billing.application.CreatePenaltyChargeCommand;
import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeSourceType;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
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
				.param("month", "6"))
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
}
