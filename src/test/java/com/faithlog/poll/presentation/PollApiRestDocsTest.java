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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.billing.application.BillingService;
import com.faithlog.billing.application.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.application.AssignCoffeeDutyCommand;
import com.faithlog.campus.application.CampusService;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
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
	private PollRepository pollRepository;

	@Autowired
	private BillingService billingService;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

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

		String directPollBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/polls", campusId)
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
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long directPollId = objectMapper.readTree(directPollBody).path("data").path("id").asLong();
		long directPollOptionId = objectMapper.readTree(directPollBody).path("data").path("options").get(0).path("id").asLong();
		openPoll(directPollId);
		long chargeCountBeforeCoffeeResponse = chargeItemRepository.count();

		mockMvc.perform(put("/api/v1/campuses/{campusId}/polls/{pollId}/responses/me", campusId, directPollId)
				.header("Authorization", "Bearer " + dutyToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "optionIds": [%d],
					  "memo": "카페라떼로 주문합니다"
					}
					""".formatted(directPollOptionId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.optionIds[0]").value(directPollOptionId))
			.andDo(document("coffee-poll-response-upsert-no-charge-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("커피 투표 ID")
				),
				requestFields(pollResponseRequestFields()),
				relaxedResponseFields(pollMyResponseFields())
			));
		org.assertj.core.api.Assertions.assertThat(chargeItemRepository.count())
			.as("커피 투표 응답 API는 응답 저장만 수행하고 COFFEE charge_items를 생성하지 않는다")
			.isEqualTo(chargeCountBeforeCoffeeResponse);

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

	@Test
	void documents_poll_response_result_missing_members_and_comment_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-poll38-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-poll38-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "38문서캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-poll38-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("docs-poll38-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		String missingToken = signupAndLogin("docs-poll38-missing@example.com", UserRole.USER);
		User missing = userRepository.findByEmail("docs-poll38-missing@example.com").orElseThrow();
		joinCampus(missingToken, campus.path("inviteCode").asText());

		JsonNode poll = createCustomPoll(managerToken, campusId, "38 비익명 투표", false, "MULTIPLE");
		long pollId = poll.path("id").asLong();
		long firstOptionId = poll.path("options").get(0).path("id").asLong();
		long secondOptionId = poll.path("options").get(1).path("id").asLong();
		openPoll(pollId);
		JsonNode anonymousPoll = createCustomPoll(managerToken, campusId, "38 익명 투표", true, "SINGLE");
		long anonymousPollId = anonymousPoll.path("id").asLong();
		long anonymousOptionId = anonymousPoll.path("options").get(0).path("id").asLong();
		openPoll(anonymousPollId);

		mockMvc.perform(put("/api/v1/campuses/{campusId}/polls/{pollId}/responses/me", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "optionIds": [%d, %d],
					  "memo": "복수 선택합니다"
					}
					""".formatted(firstOptionId, secondOptionId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.optionIds[0]").value(firstOptionId))
			.andDo(document("poll-response-upsert-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				requestFields(pollResponseRequestFields()),
				relaxedResponseFields(pollMyResponseFields())
			));

		mockMvc.perform(put("/api/v1/campuses/{campusId}/polls/{pollId}/responses/me", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "optionIds": [%d, %d],
					  "memo": "중복 선택"
					}
					""".formatted(firstOptionId, firstOptionId)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("POLL_RESPONSE_DUPLICATE_OPTION"))
			.andDo(document("poll-response-duplicate-option-error",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				requestFields(pollResponseRequestFields()),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(put("/api/v1/campuses/{campusId}/polls/{pollId}/responses/me", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "optionIds": [],
					  "memo": "빈 선택"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("POLL_RESPONSE_INVALID_SELECTION_COUNT"))
			.andExpect(jsonPath("$.message").value("투표 선택 개수가 올바르지 않습니다."))
			.andDo(document("poll-response-empty-selection-count-error",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				requestFields(emptyPollResponseRequestFields()),
				responseFields(errorResponseFields())
			));

		mockMvc.perform(put("/api/v1/campuses/{campusId}/polls/{pollId}/responses/me", campusId, anonymousPollId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "optionIds": [%d],
					  "memo": "익명 응답"
					}
					""".formatted(anonymousOptionId)))
			.andExpect(status().isOk());
		mockMvc.perform(put("/api/v1/campuses/{campusId}/polls/{pollId}/responses/me", campusId, pollId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "optionIds": [%d],
					  "memo": "관리자 응답"
					}
					""".formatted(firstOptionId)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/campuses/{campusId}/polls", campusId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == %d)]".formatted(pollId)).exists())
			.andDo(document("poll-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				relaxedResponseFields(apiResponseFields(fieldWithPath("data[]").description("조회 가능한 투표 목록")))
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/polls/{pollId}", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.myResponse.optionIds[0]").value(firstOptionId))
			.andDo(document("poll-detail-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				relaxedResponseFields(apiResponseFields(
					fieldWithPath("data.id").description("투표 ID"),
					fieldWithPath("data.options[]").description("선택지 목록"),
					fieldWithPath("data.myResponse").optional().description("현재 로그인 사용자의 응답. 미응답이면 null")
				))
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/polls/{pollId}/results", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.optionResults[0].respondents[0].userId").exists())
			.andDo(document("poll-results-non-anonymous-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				relaxedResponseFields(pollResultsFields())
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/polls/{pollId}/results", campusId, anonymousPollId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.anonymous").value(true))
			.andExpect(jsonPath("$.data.optionResults[0].respondents").isEmpty())
			.andDo(document("poll-results-anonymous-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				relaxedResponseFields(pollResultsFields())
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/polls/{pollId}/missing-members", campusId, pollId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].userId").value(missing.id()))
			.andDo(document("poll-missing-members-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				relaxedResponseFields(apiResponseFields(fieldWithPath("data[]").description("ACTIVE 멤버 중 해당 투표 응답이 없는 사용자 목록")))
			));

		String commentBody = mockMvc.perform(post("/api/v1/campuses/{campusId}/polls/{pollId}/comments", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "content": "참석합니다"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.content").value("참석합니다"))
			.andDo(document("poll-comment-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				requestFields(commentRequestFields()),
				relaxedResponseFields(commentResponseFields())
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long commentId = objectMapper.readTree(commentBody).path("data").path("commentId").asLong();

		mockMvc.perform(patch("/api/v1/campuses/{campusId}/polls/{pollId}/comments/{commentId}", campusId, pollId, commentId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "content": "관리자가 수정했습니다"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content").value("관리자가 수정했습니다"))
			.andDo(document("poll-comment-update-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID"),
					parameterWithName("commentId").description("댓글 ID")
				),
				requestFields(commentRequestFields()),
				relaxedResponseFields(commentResponseFields())
			));

		mockMvc.perform(delete("/api/v1/campuses/{campusId}/polls/{pollId}/comments/{commentId}", campusId, pollId, commentId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isNoContent())
			.andDo(document("poll-comment-delete-success",
				preprocessRequest(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID"),
					parameterWithName("commentId").description("댓글 ID")
				)
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/polls/{pollId}/comments", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].deleted").value(true))
			.andExpect(jsonPath("$.data[0].content").value("삭제된 댓글입니다."))
			.andDo(document("poll-comments-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				relaxedResponseFields(apiResponseFields(fieldWithPath("data[]").description("투표 댓글 목록")))
			));
	}

	@Test
	void documents_poll_close_and_user_option_add_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-poll97-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-poll97-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "97문서캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-poll97-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());

		String pollBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/polls", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "templateId": null,
					  "title": "사용자 항목 추가 허용 투표",
					  "pollType": "CUSTOM",
					  "selectionType": "SINGLE",
					  "isAnonymous": false,
					  "allowUserOptionAdd": true,
					  "chargeGenerationType": "NONE",
					  "paymentCategory": null,
					  "paymentAccountId": null,
					  "startsAt": "2026-06-20T00:00:00Z",
					  "endsAt": "2026-06-21T00:00:00Z",
					  "options": [
					    {"content": "기존 A", "menuId": null, "priceAmount": 0, "sortOrder": 1},
					    {"content": "기존 B", "menuId": null, "priceAmount": 0, "sortOrder": 2}
					  ]
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.allowUserOptionAdd").value(true))
			.andDo(document("poll-create-with-user-option-add-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID")),
				requestFields(pollCreateRequestFields()),
				relaxedResponseFields(pollResponseFields())
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long pollId = objectMapper.readTree(pollBody).path("data").path("id").asLong();
		openPoll(pollId);

		String optionBody = mockMvc.perform(post("/api/v1/campuses/{campusId}/polls/{pollId}/options", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "content": "새 항목"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.content").value("새 항목"))
			.andExpect(jsonPath("$.data.userAdded").value(true))
			.andDo(document("poll-user-option-add-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				requestFields(userOptionAddRequestFields()),
				relaxedResponseFields(optionResponseFields())
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long addedOptionId = objectMapper.readTree(optionBody).path("data").path("id").asLong();

		mockMvc.perform(put("/api/v1/campuses/{campusId}/polls/{pollId}/responses/me", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "optionIds": [%d],
					  "memo": "추가한 항목으로 응답"
					}
					""".formatted(addedOptionId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.optionIds[0]").value(addedOptionId));

		mockMvc.perform(post("/api/v1/campuses/{campusId}/polls/{pollId}/options", campusId, pollId)
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "content": "새 항목"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("POLL_OPTION_DUPLICATE_CONTENT"))
			.andDo(document("poll-user-option-add-duplicate-error",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				requestFields(userOptionAddRequestFields()),
				responseFields(errorResponseFields())
			));

		long chargeCountBeforeClose = chargeItemRepository.count();
		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/polls/{pollId}/close", campusId, pollId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("CLOSED"))
			.andDo(document("poll-close-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				relaxedResponseFields(pollResponseFields())
			));
		org.assertj.core.api.Assertions.assertThat(chargeItemRepository.count())
			.as("투표 종료 API는 종료만 수행하고 청구/정산을 실행하지 않는다")
			.isEqualTo(chargeCountBeforeClose);

		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/polls/{pollId}/close", campusId, pollId)
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("POLL_CLOSE_NOT_ALLOWED"))
			.andDo(document("poll-close-invalid-state-error",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("pollId").description("투표 ID")
				),
				responseFields(errorResponseFields())
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
			fieldWithPath("allowUserOptionAdd").type(JsonFieldType.BOOLEAN).optional().description("일반 사용자의 투표 항목 추가 허용 여부. 생략 시 false"),
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
			fieldWithPath("allowUserOptionAdd").type(JsonFieldType.BOOLEAN).optional().description("일반 사용자의 투표 항목 추가 허용 여부. 생략 시 false"),
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
			fieldWithPath("allowUserOptionAdd").type(JsonFieldType.BOOLEAN).optional().description("일반 사용자의 투표 항목 추가 허용 여부. 생략 시 false"),
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
			fieldWithPath("data.allowUserOptionAdd").description("일반 사용자의 투표 항목 추가 허용 여부"),
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
			fieldWithPath("data.allowUserOptionAdd").description("일반 사용자의 투표 항목 추가 허용 여부"),
			fieldWithPath("data.chargeGenerationType").description("청구 생성 방식"),
			fieldWithPath("data.paymentCategory").optional().description("청구 카테고리"),
			fieldWithPath("data.paymentAccountId").optional().description("커피 청구 계좌 ID"),
			fieldWithPath("data.startsAt").description("시작 시각"),
			fieldWithPath("data.endsAt").description("종료 시각"),
			fieldWithPath("data.status").description("투표 상태"),
			fieldWithPath("data.options[]").description("선택지 목록")
		);
	}

	private FieldDescriptor[] pollResponseRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("optionIds[]").description("선택한 투표 선택지 ID 목록"),
			fieldWithPath("memo").optional().description("응답 메모")
		};
	}

	private FieldDescriptor[] emptyPollResponseRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("optionIds").type(JsonFieldType.ARRAY).description("선택한 투표 선택지 ID 목록. 빈 배열은 POLL_RESPONSE_INVALID_SELECTION_COUNT로 실패"),
			fieldWithPath("memo").optional().description("응답 메모")
		};
	}

	private FieldDescriptor[] userOptionAddRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("content").description("추가할 투표 선택지명. 앞뒤 공백은 제거되며 기존 선택지명과 대소문자 무시 중복이면 실패")
		};
	}

	private FieldDescriptor[] optionResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.id").description("투표 선택지 ID"),
			fieldWithPath("data.content").description("선택지명"),
			fieldWithPath("data.composeMenuCode").optional().description("커피 메뉴 코드. 사용자 추가 항목은 null"),
			fieldWithPath("data.priceAmount").description("선택지 가격. 사용자 추가 항목은 0"),
			fieldWithPath("data.sortOrder").description("정렬 순서"),
			fieldWithPath("data.userAdded").description("사용자 추가 선택지 여부")
		);
	}

	private FieldDescriptor[] pollMyResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.responseId").description("응답 ID"),
			fieldWithPath("data.pollId").description("투표 ID"),
			fieldWithPath("data.optionIds[]").description("선택한 선택지 ID 목록"),
			fieldWithPath("data.memo").optional().description("응답 메모"),
			fieldWithPath("data.respondedAt").description("응답 시각")
		);
	}

	private FieldDescriptor[] pollResultsFields() {
		return apiResponseFields(
			fieldWithPath("data.pollId").description("투표 ID"),
			fieldWithPath("data.campusId").description("캠퍼스 ID"),
			fieldWithPath("data.title").description("투표 제목"),
			fieldWithPath("data.pollType").description("투표 타입"),
			fieldWithPath("data.selectionType").description("선택 방식"),
			fieldWithPath("data.anonymous").description("익명 여부"),
			fieldWithPath("data.status").description("투표 상태"),
			fieldWithPath("data.startsAt").description("시작 시각"),
			fieldWithPath("data.endsAt").description("종료 시각"),
			fieldWithPath("data.targetMemberCount").description("응답 대상 ACTIVE 멤버 수"),
			fieldWithPath("data.respondedCount").description("응답자 수"),
			fieldWithPath("data.notRespondedCount").description("미응답자 수"),
			fieldWithPath("data.optionResults[]").description("선택지별 결과"),
			fieldWithPath("data.optionResults[].id").description("선택지 ID"),
			fieldWithPath("data.optionResults[].content").description("선택지 내용"),
			fieldWithPath("data.optionResults[].sortOrder").description("정렬 순서"),
			fieldWithPath("data.optionResults[].responseCount").description("선택지 응답 수"),
			fieldWithPath("data.optionResults[].respondents[]").description("비익명 투표의 선택지별 응답자 목록. 익명 투표에서는 빈 배열")
		);
	}

	private FieldDescriptor[] commentRequestFields() {
		return new FieldDescriptor[] {
			fieldWithPath("content").description("댓글 내용")
		};
	}

	private FieldDescriptor[] commentResponseFields() {
		return apiResponseFields(
			fieldWithPath("data.commentId").description("댓글 ID"),
			fieldWithPath("data.pollId").description("투표 ID"),
			fieldWithPath("data.userId").description("작성자 ID"),
			fieldWithPath("data.name").description("작성자 이름"),
			fieldWithPath("data.content").description("댓글 내용. 삭제된 댓글이면 삭제 안내 문구"),
			fieldWithPath("data.deleted").description("삭제 여부"),
			fieldWithPath("data.createdAt").description("생성 시각"),
			fieldWithPath("data.updatedAt").description("수정 시각")
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

	private JsonNode createCustomPoll(String accessToken, long campusId, String title, boolean anonymous, String selectionType) throws Exception {
		String body = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/polls", campusId)
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "templateId": null,
					  "title": "%s",
					  "pollType": "CUSTOM",
					  "selectionType": "%s",
					  "isAnonymous": %s,
					  "chargeGenerationType": "NONE",
					  "paymentCategory": null,
					  "paymentAccountId": null,
					  "startsAt": "2026-06-20T00:00:00Z",
					  "endsAt": "2026-06-21T00:00:00Z",
					  "options": [
					    {"content": "참석", "menuId": null, "priceAmount": 0, "sortOrder": 1},
					    {"content": "불참", "menuId": null, "priceAmount": 0, "sortOrder": 2}
					  ]
					}
					""".formatted(title, selectionType, anonymous)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private void openPoll(long pollId) {
		com.faithlog.poll.domain.Poll poll = pollRepository.findById(pollId).orElseThrow();
		ReflectionTestUtils.setField(poll, "status", PollStatus.OPEN);
		ReflectionTestUtils.setField(poll, "startsAt", java.time.Instant.now().minusSeconds(60));
		ReflectionTestUtils.setField(poll, "endsAt", java.time.Instant.now().plusSeconds(3600));
		pollRepository.saveAndFlush(poll);
	}
}
