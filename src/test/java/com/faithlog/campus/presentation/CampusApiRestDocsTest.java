package com.faithlog.campus.presentation;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class CampusApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Test
	void documents_campus_create_join_me_and_detail_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-campus-manager@example.com", UserRole.MANAGER);

		String createBody = mockMvc.perform(post("/api/v1/campuses")
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "30캠",
					  "region": "분당",
					  "description": "분당 30캠퍼스"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.inviteCode").isString())
			.andExpect(jsonPath("$.data.myCampusRole").value("MINISTER"))
			.andExpect(jsonPath("$.data.membershipStatus").value("ACTIVE"))
			.andDo(document("campus-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				requestFields(
					fieldWithPath("name").description("생성할 캠퍼스 이름"),
					fieldWithPath("region").optional().description("캠퍼스 지역"),
					fieldWithPath("description").optional().description("캠퍼스 설명")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data.campusId").description("생성된 캠퍼스 ID"),
					fieldWithPath("data.name").description("캠퍼스 이름"),
					fieldWithPath("data.region").optional().description("캠퍼스 지역"),
					fieldWithPath("data.description").optional().description("캠퍼스 설명"),
					fieldWithPath("data.inviteCode").description("생성된 초대코드"),
					fieldWithPath("data.myCampusRole").description("생성자의 캠퍼스 내부 역할. `MINISTER`"),
					fieldWithPath("data.membershipStatus").description("생성자의 캠퍼스 멤버십 상태. `ACTIVE`")
				))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();

		JsonNode campus = objectMapper.readTree(createBody).path("data");
		long campusId = campus.path("campusId").asLong();
		String inviteCode = campus.path("inviteCode").asText();
		String memberToken = signupAndLogin("docs-campus-member@example.com", UserRole.USER);

		mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "%s"
					}
					""".formatted(inviteCode)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.campusRole").value("MEMBER"))
			.andExpect(jsonPath("$.data.status").value("ACTIVE"))
			.andDo(document("campus-join-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				requestFields(
					fieldWithPath("inviteCode").description("가입할 캠퍼스 초대코드")
				),
				responseFields(apiResponseFields(membershipFields("data.")))
			));

		mockMvc.perform(get("/api/v1/campuses/me")
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].campusId").value(campusId))
			.andDo(document("campuses-me-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				responseFields(apiResponseFields(
					fieldWithPath("data[]").description("현재 사용자의 ACTIVE 캠퍼스 멤버십 목록"),
					fieldWithPath("data[].membershipId").description("캠퍼스 멤버십 ID"),
					fieldWithPath("data[].campusId").description("캠퍼스 ID"),
					fieldWithPath("data[].campusName").description("캠퍼스 이름"),
					fieldWithPath("data[].region").optional().description("캠퍼스 지역"),
					fieldWithPath("data[].campusRole").description("현재 사용자의 캠퍼스 내부 역할"),
					fieldWithPath("data[].status").description("멤버십 상태. MVP에서는 ACTIVE만 반환")
				))
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inviteCode").doesNotExist())
			.andExpect(jsonPath("$.data.myCampusRole").value("MEMBER"))
			.andDo(document("campus-detail-member-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				responseFields(apiResponseFields(campusDetailFields(false)))
			));

		String adminToken = signupAndLogin("docs-campus-admin@example.com", UserRole.ADMIN);

		mockMvc.perform(get("/api/v1/campuses/{campusId}", campusId)
				.header("Authorization", "Bearer " + adminToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.inviteCode").value(inviteCode))
			.andExpect(jsonPath("$.data.myCampusRole").value(nullValue()))
			.andExpect(jsonPath("$.data.membershipStatus").value(nullValue()))
			.andDo(document("campus-detail-admin-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				responseFields(apiResponseFields(campusDetailFields(true)))
			));
	}

	@Test
	void documents_campus_join_invalid_invite_code() throws Exception {
		String memberToken = signupAndLogin("docs-campus-invalid-invite@example.com", UserRole.USER);

		mockMvc.perform(post("/api/v1/campuses/join")
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "inviteCode": "NO-SUCH-CODE"
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"))
			.andExpect(jsonPath("$.message").value("유효하지 않은 초대코드입니다."))
			.andDo(document("campus-join-invalid-invite-code",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				requestFields(
					fieldWithPath("inviteCode").description("존재하지 않는 초대코드")
				),
				responseFields(errorResponseFields())
			));
	}

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "문서회원",
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
		FieldDescriptor[] fields = new FieldDescriptor[5 + dataFields.length];
		fields[0] = fieldWithPath("success").description("요청 성공 여부");
		fields[1] = fieldWithPath("code").description("공통 응답 코드");
		fields[2] = fieldWithPath("message").description("응답 메시지");
		fields[3] = fieldWithPath("data").description("응답 데이터");
		fields[4] = fieldWithPath("timestamp").description("응답 생성 시각");
		System.arraycopy(dataFields, 0, fields, 5, dataFields.length);
		return fields;
	}

	private static FieldDescriptor[] membershipFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "membershipId").description("캠퍼스 멤버십 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "campusName").description("캠퍼스 이름"),
			fieldWithPath(prefix + "region").optional().description("캠퍼스 지역"),
			fieldWithPath(prefix + "campusRole").description("현재 사용자의 캠퍼스 내부 역할"),
			fieldWithPath(prefix + "status").description("멤버십 상태")
		};
	}

	private static FieldDescriptor[] campusDetailFields(boolean includeInviteCode) {
		FieldDescriptor[] commonFields = new FieldDescriptor[] {
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.name").description("캠퍼스 이름"),
			fieldWithPath("data.region").optional().description("캠퍼스 지역"),
			fieldWithPath("data.description").optional().description("캠퍼스 설명"),
			fieldWithPath("data.isActive").description("캠퍼스 활성 여부"),
			fieldWithPath("data.myCampusRole").optional().description("현재 사용자의 캠퍼스 내부 역할. ADMIN이 멤버가 아니면 null"),
			fieldWithPath("data.membershipStatus").optional().description("현재 사용자의 캠퍼스 멤버십 상태. ADMIN이 멤버가 아니면 null")
		};
		if (!includeInviteCode) {
			return commonFields;
		}

		FieldDescriptor[] fields = new FieldDescriptor[commonFields.length + 1];
		System.arraycopy(commonFields, 0, fields, 0, commonFields.length);
		fields[commonFields.length] = fieldWithPath("data.inviteCode").description("조회 권한이 있을 때 노출되는 초대코드");
		return fields;
	}

	private static FieldDescriptor[] errorResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 실패 응답에서는 false"),
			fieldWithPath("code").description("오류 코드"),
			fieldWithPath("message").description("오류 메시지"),
			fieldWithPath("data").optional().description("실패 응답에서는 null"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
	}
}
