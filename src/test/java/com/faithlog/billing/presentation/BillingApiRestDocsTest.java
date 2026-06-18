package com.faithlog.billing.presentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
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
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
class BillingApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Test
	void documents_payment_account_create_list_and_deactivate_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-billing-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-billing-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "48캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-billing-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());

		String createBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "PENALTY",
					  "nickname": "48캠 벌금 계좌",
					  "bankName": "카카오뱅크",
					  "accountNumber": "3333-00-7777777",
					  "accountHolder": "회계",
					  "ownerUserId": %d
					}
					""".formatted(manager.id())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.accountType").value("PENALTY"))
			.andExpect(jsonPath("$.data.accountNumber").value("3333-00-7777777"))
			.andDo(document("payment-account-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("계좌를 등록할 캠퍼스 ID")),
				requestFields(
					fieldWithPath("accountType").description("계좌 유형. `PENALTY` 또는 `COFFEE`"),
					fieldWithPath("nickname").description("계좌 별칭"),
					fieldWithPath("bankName").description("은행명"),
					fieldWithPath("accountNumber").description("계좌번호. 납부에 필요하므로 전체 저장 및 노출"),
					fieldWithPath("accountHolder").description("예금주"),
					fieldWithPath("ownerUserId").optional().description("계좌 소유 사용자 ID. 없으면 null")
				),
				responseFields(apiResponseFields(adminAccountFields("data.")))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long accountId = objectMapper.readTree(createBody).path("data").path("id").asLong();

		mockMvc.perform(get("/api/v1/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data[0].accountNumber").value("3333-00-7777777"))
			.andExpect(jsonPath("$.data[0].ownerUserId").doesNotExist())
			.andExpect(jsonPath("$.data[0].isActive").doesNotExist())
			.andDo(document("payment-account-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("납부 계좌를 조회할 캠퍼스 ID")),
				responseFields(apiResponseFields(
					fieldWithPath("data[]").description("현재 활성 납부 계좌 목록"),
					fieldWithPath("data[].id").description("납부 계좌 ID"),
					fieldWithPath("data[].accountType").description("계좌 유형"),
					fieldWithPath("data[].nickname").description("계좌 별칭"),
					fieldWithPath("data[].bankName").description("은행명"),
					fieldWithPath("data[].accountNumber").description("전체 계좌번호"),
					fieldWithPath("data[].accountHolder").description("예금주")
				))
			));

		mockMvc.perform(patch("/api/v1/admin/payment-accounts/{accountId}/deactivate", accountId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.id").value(accountId))
			.andExpect(jsonPath("$.data.isActive").value(false))
			.andDo(document("payment-account-deactivate-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("accountId").description("비활성화할 납부 계좌 ID")),
				responseFields(apiResponseFields(adminAccountFields("data.")))
			));
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
					  "name": "문서빌링",
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

	private static org.springframework.restdocs.headers.RequestHeadersSnippet authHeader() {
		return requestHeaders(
			headerWithName("Authorization").description("`Bearer {accessToken}` 형식의 Access Token")
		);
	}

	private static FieldDescriptor[] apiResponseFields(FieldDescriptor... dataFields) {
		FieldDescriptor[] commonFields = new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부"),
			fieldWithPath("code").description("응답 코드"),
			fieldWithPath("message").description("응답 메시지"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
		FieldDescriptor[] fields = new FieldDescriptor[commonFields.length + dataFields.length];
		System.arraycopy(commonFields, 0, fields, 0, commonFields.length);
		System.arraycopy(dataFields, 0, fields, commonFields.length, dataFields.length);
		return fields;
	}

	private static FieldDescriptor[] adminAccountFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "id").description("납부 계좌 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "accountType").description("계좌 유형"),
			fieldWithPath(prefix + "nickname").description("계좌 별칭"),
			fieldWithPath(prefix + "bankName").description("은행명"),
			fieldWithPath(prefix + "accountNumber").description("전체 계좌번호"),
			fieldWithPath(prefix + "accountHolder").description("예금주"),
			fieldWithPath(prefix + "ownerUserId").optional().description("계좌 소유 사용자 ID"),
			fieldWithPath(prefix + "isActive").description("계좌 활성 여부")
		};
	}
}
