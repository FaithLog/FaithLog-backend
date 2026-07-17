package com.faithlog.devotion.controller;

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
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
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
class PenaltyRuleApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Test
	void documents_penalty_rule_create_list_and_update_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-penalty-rule-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "95캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-penalty-rule-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());

		String createdBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/penalty-rules", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ruleType": "QUIET_TIME",
					  "calculationType": "MISSING_COUNT",
					  "requiredCount": 5,
					  "baseAmount": 0,
					  "amountPerUnit": 500
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.ruleType").value("QUIET_TIME"))
			.andExpect(jsonPath("$.data.calculationType").value("MISSING_COUNT"))
			.andExpect(jsonPath("$.data.requiredCount").value(5))
			.andExpect(jsonPath("$.data.baseAmount").value(0))
			.andExpect(jsonPath("$.data.amountPerUnit").value(500))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andDo(document("penalty-rule-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("벌금 규칙을 생성할 캠퍼스 ID")),
				requestFields(createRequestFields()),
				responseFields(apiResponseFields(ruleResponseFields()))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long ruleId = objectMapper.readTree(createdBody).path("data").path("id").asLong();

		mockMvc.perform(get("/api/v1/campuses/{campusId}/penalty-rules", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(ruleId))
			.andDo(document("penalty-rule-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("벌금 규칙을 조회할 캠퍼스 ID")),
				responseFields(apiResponseFields(
					fieldWithPath("data[]").description("활성/비활성 벌금 규칙 목록"),
					fieldWithPath("data[].id").description("벌금 규칙 ID"),
					fieldWithPath("data[].ruleType").description("벌금 규칙 타입"),
					fieldWithPath("data[].calculationType").description("계산 타입"),
					fieldWithPath("data[].requiredCount").description("필수 기준 횟수"),
					fieldWithPath("data[].baseAmount").description("기본 금액"),
					fieldWithPath("data[].amountPerUnit").description("단위당 금액"),
					fieldWithPath("data[].isActive").description("활성 여부")
				))
			));

		mockMvc.perform(patch("/api/v1/admin/penalty-rules/{ruleId}", ruleId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "requiredCount": 6,
					  "baseAmount": 0,
					  "amountPerUnit": 600,
					  "isActive": false
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.requiredCount").value(6))
			.andExpect(jsonPath("$.data.amountPerUnit").value(600))
			.andExpect(jsonPath("$.data.isActive").value(false))
			.andDo(document("penalty-rule-update-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("ruleId").description("수정할 벌금 규칙 ID")),
				requestFields(updateRequestFields()),
				responseFields(apiResponseFields(ruleResponseFields()))
			));
	}

	@Test
	void documents_penalty_rule_invalid_type_pair_and_negative_value_errors() throws Exception {
		String managerToken = signupAndLogin("docs-penalty-rule-invalid-manager@example.com", UserRole.MANAGER);
		JsonNode campus = createCampus(managerToken, "96캠");

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/penalty-rules", campus.path("campusId").asLong())
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ruleType": "SATURDAY_LATE",
					  "calculationType": "MISSING_COUNT",
					  "requiredCount": 0,
					  "baseAmount": 1000,
					  "amountPerUnit": 100
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEVOTION_PENALTY_RULE_INVALID_TYPE_PAIR"))
			.andDo(document("penalty-rule-invalid-type-pair",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				requestFields(createRequestFields()),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/penalty-rules", campus.path("campusId").asLong())
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ruleType": "QUIET_TIME",
					  "calculationType": "MISSING_COUNT",
					  "requiredCount": -1,
					  "baseAmount": 0,
					  "amountPerUnit": 500
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("DEVOTION_PENALTY_RULE_INVALID_VALUE"))
			.andDo(document("penalty-rule-invalid-negative-value",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				requestFields(createRequestFields()),
				responseFields(errorResponseFields())
			));
	}

	private FieldDescriptor[] createRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("ruleType").description("벌금 규칙 타입. QUIET_TIME, PRAYER, BIBLE_READING, SATURDAY_LATE"),
			fieldWithPath("calculationType").description("계산 타입. MISSING_COUNT 또는 LATE_MINUTE"),
			fieldWithPath("requiredCount").description("필수 기준 횟수. 0 이상"),
			fieldWithPath("baseAmount").description("기본 금액. 0 이상"),
			fieldWithPath("amountPerUnit").description("단위당 금액. 0 이상")
		};
	}

	private FieldDescriptor[] updateRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("requiredCount").description("필수 기준 횟수. 0 이상"),
			fieldWithPath("baseAmount").description("기본 금액. 0 이상"),
			fieldWithPath("amountPerUnit").description("단위당 금액. 0 이상"),
			fieldWithPath("isActive").description("활성 여부")
		};
	}

	private FieldDescriptor[] ruleResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("data.id").description("벌금 규칙 ID"),
			fieldWithPath("data.ruleType").description("벌금 규칙 타입"),
			fieldWithPath("data.calculationType").description("계산 타입"),
			fieldWithPath("data.requiredCount").description("필수 기준 횟수"),
			fieldWithPath("data.baseAmount").description("기본 금액"),
			fieldWithPath("data.amountPerUnit").description("단위당 금액"),
			fieldWithPath("data.isActive").description("활성 여부")
		};
	}

	private FieldDescriptor[] apiResponseFields(FieldDescriptor... dataFields) {
		FieldDescriptor[] commonFields = new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부"),
			fieldWithPath("code").description("응답 코드"),
			fieldWithPath("message").description("응답 메시지"),
			fieldWithPath("data").description("응답 데이터"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
		FieldDescriptor[] fields = new FieldDescriptor[commonFields.length + dataFields.length];
		System.arraycopy(commonFields, 0, fields, 0, commonFields.length);
		System.arraycopy(dataFields, 0, fields, commonFields.length, dataFields.length);
		return fields;
	}

	private FieldDescriptor[] errorResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 실패 시 false"),
			fieldWithPath("code").description("상세 에러 코드"),
			fieldWithPath("message").description("사용자 표시용 에러 메시지"),
			fieldWithPath("data").description("실패 시 null"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}

	private org.springframework.restdocs.headers.RequestHeadersSnippet authHeader() {
		return requestHeaders(headerWithName("Authorization").description("Bearer access token"));
	}

	private JsonNode createCampus(String accessToken, String name) throws Exception {
		String body = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "%s",
					  "region": "분당",
					  "description": "분당 %s"
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
					  "name": "문서벌금",
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
