package com.faithlog.poll.presentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.campus.application.AssignCoffeeDutyCommand;
import com.faithlog.campus.application.CampusService;
import com.faithlog.poll.infrastructure.jpa.CoffeeMenuCatalogRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
class PollApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CoffeeMenuCatalogRepository coffeeMenuCatalogRepository;

	@Autowired
	private BillingService billingService;

	@Autowired
	private CampusService campusService;

	@Test
	void documents_coffee_catalog_template_and_poll_create_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-poll-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-poll-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "37문서캠");
		long campusId = campus.path("campusId").asLong();
		String dutyToken = signupAndLogin("docs-poll-duty@example.com", UserRole.USER);
		User duty = userRepository.findByEmail("docs-poll-duty@example.com").orElseThrow();
		joinCampus(dutyToken, campus.path("inviteCode").asText());
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campusId, manager.id(), duty.id()));
		long coffeeAccountId = createCoffeeAccount(campusId, manager.id());
		long americanoMenuId = menuId("AMERICANO_HOT");
		long latteMenuId = menuId("CAFE_LATTE");

		String brandsBody = mockMvc.perform(get("/api/v1/coffee-brands")
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].brandCode").value("COMPOSE_COFFEE"))
			.andDo(document("coffee-brands-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data[]").description("활성 커피 브랜드 목록"),
					fieldWithPath("data[].id").description("브랜드 ID"),
					fieldWithPath("data[].brandCode").description("브랜드 코드"),
					fieldWithPath("data[].name").description("브랜드 이름"),
					fieldWithPath("data[].sortOrder").description("정렬 순서")
				))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long brandId = objectMapper.readTree(brandsBody).path("data").get(0).path("id").asLong();

		mockMvc.perform(get("/api/v1/coffee-brands/{brandId}/menus", brandId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.menuCode == 'AMERICANO_HOT')].priceAmount").value(1500))
			.andExpect(jsonPath("$.data[?(@.menuCode == 'AMERICANO_ICE')].priceAmount").value(1800))
			.andDo(document("coffee-brand-menus-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("brandId").description("커피 브랜드 ID")),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data[]").description("활성 메뉴 목록"),
					fieldWithPath("data[].id").description("메뉴 ID"),
					fieldWithPath("data[].brandId").description("브랜드 ID"),
					fieldWithPath("data[].menuCode").description("메뉴 코드"),
					fieldWithPath("data[].name").description("메뉴명"),
					fieldWithPath("data[].priceAmount").description("가격"),
					fieldWithPath("data[].category").description("메뉴 카테고리"),
					fieldWithPath("data[].sortOrder").description("정렬 순서")
				))
			));

		String templateBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/poll-templates", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "커스텀 커피 투표",
					  "pollType": "COFFEE",
					  "selectionType": "SINGLE",
					  "chargeGenerationType": "OPTION_PRICE",
					  "paymentCategory": "COFFEE",
					  "paymentAccountId": %d,
					  "autoCreateEnabled": false,
					  "startDayOfWeek": 1,
					  "startTime": "09:00:00",
					  "endDayOfWeek": 1,
					  "endTime": "18:00:00",
					  "options": [
					    {"content": null, "menuId": %d, "priceAmount": null, "sortOrder": 1}
					  ]
					}
					""".formatted(coffeeAccountId, americanoMenuId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.options[0].composeMenuCode").value("AMERICANO_HOT"))
			.andExpect(jsonPath("$.data.options[0].priceAmount").value(1500))
			.andDo(document("poll-template-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				requestFields(templateRequestFields()),
				relaxedResponseFields(templateResponseFields())
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long templateId = objectMapper.readTree(templateBody).path("data").path("id").asLong();
		JsonNode otherCampus = createCampus(managerToken, "37문서다른캠");
		long otherCampusId = otherCampus.path("campusId").asLong();

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/poll-templates", campusId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andDo(document("poll-template-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				relaxedResponseFields(apiResponseFields(fieldWithPath("data[]").description("활성 템플릿 목록")))
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/poll-templates/{templateId}", campusId, templateId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andDo(document("poll-template-detail-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("templateId").description("투표 템플릿 ID")
				),
				relaxedResponseFields(templateResponseFields())
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/poll-templates/{templateId}", otherCampusId, templateId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("POLL_TEMPLATE_NOT_FOUND"))
			.andDo(document("poll-template-detail-campus-scope-mismatch",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("templateId").description("투표 템플릿 ID")
				),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/poll-templates/{templateId}", campusId, templateId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "수정된 투표 템플릿",
					  "selectionType": "MULTIPLE",
					  "chargeGenerationType": "NONE",
					  "paymentCategory": null,
					  "paymentAccountId": null,
					  "autoCreateEnabled": true,
					  "startDayOfWeek": 2,
					  "startTime": "10:00:00",
					  "endDayOfWeek": 4,
					  "endTime": "17:30:00",
					  "options": [
					    {"content": "참석", "menuId": null, "priceAmount": 0, "sortOrder": 1},
					    {"content": "불참", "menuId": null, "priceAmount": 0, "sortOrder": 2}
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.autoCreateEnabled").value(true))
			.andDo(document("poll-template-update-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("templateId").description("투표 템플릿 ID")
				),
				requestFields(updateTemplateRequestFields()),
				relaxedResponseFields(templateResponseFields())
			));

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/polls", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "templateId": null,
					  "title": "직접 커피 투표",
					  "pollType": "COFFEE",
					  "selectionType": "SINGLE",
					  "isAnonymous": false,
					  "chargeGenerationType": "OPTION_PRICE",
					  "paymentCategory": "COFFEE",
					  "paymentAccountId": %d,
					  "startsAt": "2026-06-22T00:00:00Z",
					  "endsAt": "2026-06-22T09:00:00Z",
					  "options": [
					    {"content": null, "menuId": %d, "priceAmount": null, "sortOrder": 1}
					  ]
					}
					""".formatted(coffeeAccountId, latteMenuId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.options[0].composeMenuCode").value("CAFE_LATTE"))
			.andDo(document("poll-create-direct-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				requestFields(pollCreateRequestFields()),
				relaxedResponseFields(pollResponseFields())
			));

		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/poll-templates/{templateId}", campusId, templateId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.isActive").value(false))
			.andDo(document("poll-template-deactivate-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("templateId").description("투표 템플릿 ID")
				),
				relaxedResponseFields(templateResponseFields())
			));
	}

	private FieldDescriptor[] templateRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("title").description("템플릿 제목"),
			fieldWithPath("pollType").description("투표 타입"),
			fieldWithPath("selectionType").description("선택 방식"),
			fieldWithPath("chargeGenerationType").description("청구 생성 방식"),
			fieldWithPath("paymentCategory").optional().description("청구 카테고리"),
			fieldWithPath("paymentAccountId").optional().description("커피 청구 계좌 ID"),
			fieldWithPath("autoCreateEnabled").description("자동 생성 설정 여부"),
			fieldWithPath("startDayOfWeek").description("시작 요일. 1=월요일, 7=일요일"),
			fieldWithPath("startTime").description("시작 시간"),
			fieldWithPath("endDayOfWeek").description("마감 요일. 1=월요일, 7=일요일"),
			fieldWithPath("endTime").description("마감 시간"),
			fieldWithPath("options[]").description("템플릿 선택지"),
			fieldWithPath("options[].content").optional().description("직접 선택지명. menuId가 있으면 생략 가능"),
			fieldWithPath("options[].menuId").optional().description("커피 메뉴 ID"),
			fieldWithPath("options[].priceAmount").optional().description("직접 선택지 가격"),
			fieldWithPath("options[].sortOrder").description("정렬 순서")
		};
	}

	private FieldDescriptor[] updateTemplateRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("title").description("템플릿 제목"),
			fieldWithPath("selectionType").description("선택 방식"),
			fieldWithPath("chargeGenerationType").description("청구 생성 방식"),
			fieldWithPath("paymentCategory").optional().description("청구 카테고리"),
			fieldWithPath("paymentAccountId").optional().description("커피 청구 계좌 ID"),
			fieldWithPath("autoCreateEnabled").description("자동 생성 설정 여부"),
			fieldWithPath("startDayOfWeek").description("시작 요일. 1=월요일, 7=일요일"),
			fieldWithPath("startTime").description("시작 시간"),
			fieldWithPath("endDayOfWeek").description("마감 요일. 1=월요일, 7=일요일"),
			fieldWithPath("endTime").description("마감 시간"),
			fieldWithPath("options[]").description("템플릿 선택지"),
			fieldWithPath("options[].content").optional().description("직접 선택지명"),
			fieldWithPath("options[].menuId").optional().description("커피 메뉴 ID"),
			fieldWithPath("options[].priceAmount").optional().description("직접 선택지 가격"),
			fieldWithPath("options[].sortOrder").description("정렬 순서")
		};
	}

	private FieldDescriptor[] pollCreateRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("templateId").optional().description("템플릿 ID. null이면 직접 선택지로 생성"),
			fieldWithPath("title").description("투표 제목"),
			fieldWithPath("pollType").optional().description("투표 타입"),
			fieldWithPath("selectionType").optional().description("선택 방식"),
			fieldWithPath("isAnonymous").description("익명 여부"),
			fieldWithPath("chargeGenerationType").optional().description("청구 생성 방식"),
			fieldWithPath("paymentCategory").optional().description("청구 카테고리"),
			fieldWithPath("paymentAccountId").optional().description("커피 청구 계좌 ID"),
			fieldWithPath("startsAt").description("투표 시작 시각"),
			fieldWithPath("endsAt").description("투표 종료 시각"),
			fieldWithPath("options[]").optional().description("직접 선택지 목록. 템플릿 기반 생성 시 사용하지 않음"),
			fieldWithPath("options[].content").optional().description("직접 선택지명"),
			fieldWithPath("options[].menuId").optional().description("커피 메뉴 ID"),
			fieldWithPath("options[].priceAmount").optional().description("직접 선택지 가격"),
			fieldWithPath("options[].sortOrder").optional().description("정렬 순서")
		};
	}

	private FieldDescriptor[] templateResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.id").description("템플릿 ID"),
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.title").description("템플릿 제목"),
			fieldWithPath("data.pollType").description("투표 타입"),
			fieldWithPath("data.selectionType").description("선택 방식"),
			fieldWithPath("data.chargeGenerationType").description("청구 생성 방식"),
			fieldWithPath("data.paymentCategory").optional().description("청구 카테고리"),
			fieldWithPath("data.paymentAccountId").optional().description("커피 청구 계좌 ID"),
			fieldWithPath("data.autoCreateEnabled").description("자동 생성 설정 여부"),
			fieldWithPath("data.startDayOfWeek").description("시작 요일"),
			fieldWithPath("data.startTime").description("시작 시간"),
			fieldWithPath("data.endDayOfWeek").description("마감 요일"),
			fieldWithPath("data.endTime").description("마감 시간"),
			fieldWithPath("data.isDefault").description("기본 템플릿 여부"),
			fieldWithPath("data.isActive").description("활성 여부"),
			fieldWithPath("data.options[]").description("선택지 목록")
		);
	}

	private FieldDescriptor[] pollResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.id").description("투표 ID"),
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.templateId").optional().description("원본 템플릿 ID"),
			fieldWithPath("data.title").description("투표 제목"),
			fieldWithPath("data.pollType").description("투표 타입"),
			fieldWithPath("data.selectionType").description("선택 방식"),
			fieldWithPath("data.isAnonymous").description("익명 여부"),
			fieldWithPath("data.chargeGenerationType").description("청구 생성 방식"),
			fieldWithPath("data.paymentCategory").optional().description("청구 카테고리"),
			fieldWithPath("data.paymentAccountId").optional().description("커피 청구 계좌 ID"),
			fieldWithPath("data.startsAt").description("시작 시각"),
			fieldWithPath("data.endsAt").description("종료 시각"),
			fieldWithPath("data.status").description("투표 상태"),
			fieldWithPath("data.options[]").description("선택지 목록")
		);
	}

	private FieldDescriptor[] errorResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 오류 응답에서는 false"),
			fieldWithPath("code").description("HTTP status와 함께 고정 계약으로 사용하는 세부 오류 코드"),
			fieldWithPath("message").description("사용자 표시용 오류 메시지"),
			fieldWithPath("data").type(JsonFieldType.NULL).description("오류 응답에서는 null"),
			fieldWithPath("timestamp").description("응답 시각")
		};
	}

	private FieldDescriptor[] apiResponseFields(FieldDescriptor... dataFields) {
		FieldDescriptor[] common = new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부"),
			fieldWithPath("code").description("응답 코드"),
			fieldWithPath("message").description("응답 메시지"),
			fieldWithPath("timestamp").description("응답 시각")
		};
		FieldDescriptor[] combined = new FieldDescriptor[common.length + dataFields.length];
		System.arraycopy(common, 0, combined, 0, common.length);
		System.arraycopy(dataFields, 0, combined, common.length, dataFields.length);
		return combined;
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
					  "name": "투표문서",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

		User user = userRepository.findByEmail(email).orElseThrow();
		ReflectionTestUtils.setField(user, "role", role);
		userRepository.saveAndFlush(user);

		String body = mockMvc.perform(post("/api/v1/auth/login")
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
		return objectMapper.readTree(body).path("data").path("accessToken").asText();
	}

	private long createCoffeeAccount(Long campusId, Long managerId) {
		return billingService.createPaymentAccount(new CreatePaymentAccountCommand(
			campusId,
			managerId,
			PaymentCategory.COFFEE,
			"커피 계좌",
			"카카오뱅크",
			"3333-37-000002",
			"커피회계",
			null
		)).id();
	}

	private long menuId(String menuCode) {
		return coffeeMenuCatalogRepository.findByMenuCode(menuCode).orElseThrow().id();
	}
}
