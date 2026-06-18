package com.faithlog.billing.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
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
}
