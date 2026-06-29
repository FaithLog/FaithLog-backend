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
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
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
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.application.AssignCoffeeDutyCommand;
import com.faithlog.campus.application.CampusService;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusRole;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
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

	@Autowired
	private BillingService billingService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Test
	void documents_payment_account_create_list_and_deactivate_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-billing-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-billing-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "48캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-billing-member@example.com", UserRole.USER);
		joinCampus(memberToken, campus.path("inviteCode").asText());
		String dutyToken = signupAndLogin("docs-billing-coffee-duty@example.com", UserRole.USER);
		User duty = userRepository.findByEmail("docs-billing-coffee-duty@example.com").orElseThrow();
		joinCampus(dutyToken, campus.path("inviteCode").asText());
		campusService.assignCoffeeDuty(new AssignCoffeeDutyCommand(campusId, manager.id(), duty.id()));

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

		String coffeeAccountBody = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/payment-accounts", campusId)
				.header("Authorization", "Bearer " + dutyToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "accountType": "COFFEE",
					  "nickname": "48캠 커피 계좌",
					  "bankName": "카카오뱅크",
					  "accountNumber": "3333-48-000001",
					  "accountHolder": "커피회계",
					  "ownerUserId": %d
					}
					""".formatted(duty.id())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.accountType").value("COFFEE"))
			.andExpect(jsonPath("$.data.isActive").value(true))
			.andDo(document("coffee-duty-payment-account-create-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("COFFEE 계좌를 등록할 캠퍼스 ID")),
				requestFields(
					fieldWithPath("accountType").description("계좌 유형. 커피 담당자는 `COFFEE`만 등록 가능"),
					fieldWithPath("nickname").description("계좌 별칭"),
					fieldWithPath("bankName").description("은행명"),
					fieldWithPath("accountNumber").description("계좌번호"),
					fieldWithPath("accountHolder").description("예금주"),
					fieldWithPath("ownerUserId").optional().description("계좌 소유 사용자 ID. 없으면 null")
				),
				responseFields(apiResponseFields(adminAccountFields("data.")))
			))
			.andReturn()
			.getResponse()
			.getContentAsString();
		long coffeeAccountId = objectMapper.readTree(coffeeAccountBody).path("data").path("id").asLong();

		mockMvc.perform(patch("/api/v1/admin/payment-accounts/{accountId}/deactivate", coffeeAccountId)
				.header("Authorization", "Bearer " + dutyToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accountType").value("COFFEE"))
			.andExpect(jsonPath("$.data.isActive").value(false))
			.andDo(document("coffee-duty-payment-account-deactivate-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("accountId").description("비활성화할 COFFEE 납부 계좌 ID")),
				responseFields(apiResponseFields(adminAccountFields("data.")))
			));
	}

	@Test
	void documents_charge_payment_completion_and_admin_status_change_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-billing-status-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-billing-status-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "49캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-billing-status-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("docs-billing-status-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-013");
		ChargeItemResult paidTarget = createPenaltyCharge(campusId, member.id(), 7001L);
		ChargeItemResult waiveTarget = createPenaltyCharge(campusId, member.id(), 7002L);

		mockMvc.perform(patch("/api/v1/campuses/{campusId}/charges/me/{chargeItemId}/paid", campusId, paidTarget.id())
				.header("Authorization", "Bearer " + memberToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "paidAt": "2026-06-12T12:30:00Z"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.status").value("PAID"))
			.andExpect(jsonPath("$.data.paidAt").value("2026-06-12T12:30:00Z"))
			.andDo(document("charge-my-paid-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("납부 완료 처리할 청구의 캠퍼스 ID"),
					parameterWithName("chargeItemId").description("납부 완료 처리할 청구 항목 ID")
				),
				requestFields(
					fieldWithPath("paidAt").optional().description("선택 입력 납부 완료 시각. 없으면 서버 시간이 사용되며, `2026-06-12T12:30:00Z` 같은 Instant 형식을 사용")
				),
				responseFields(apiResponseFields(chargeFields("data.")))
			));

		mockMvc.perform(patch("/api/v1/admin/charges/{chargeItemId}/status", waiveTarget.id())
				.header("Authorization", "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "WAIVED"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.status").value("WAIVED"))
			.andDo(document("charge-admin-status-change-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("chargeItemId").description("상태를 변경할 청구 항목 ID")),
				requestFields(
					fieldWithPath("status").description("변경할 청구 상태. 관리자 요청은 `UNPAID`, `WAIVED`, `CANCELED`만 허용")
				),
				responseFields(apiResponseFields(chargeFields("data.")))
			));
	}

	@Test
	void documents_charge_query_contracts() throws Exception {
		String managerToken = signupAndLogin("docs-billing-query-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-billing-query-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "50캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-billing-query-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("docs-billing-query-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-018");
		ChargeItemResult unpaid = createPenaltyCharge(campusId, member.id(), 7101L);
		ChargeItemResult paidResult = createPenaltyCharge(campusId, member.id(), 7102L);
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
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.items[0].id").value(unpaid.id()))
			.andDo(document("charge-my-list-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("청구 목록을 조회할 캠퍼스 ID")),
				queryParameters(
					parameterWithName("paymentCategory").optional().description("청구 유형 필터. `PENALTY` 또는 `COFFEE`"),
					parameterWithName("status").optional().description("청구 상태 필터. `UNPAID`, `PAID`, `WAIVED`, `CANCELED`"),
					parameterWithName("page").optional().description("페이지 번호. 기본 0"),
					parameterWithName("size").optional().description("페이지 크기. 기본 20, 최대 100"),
					parameterWithName("sort").optional().description("정렬. 기본 `createdAt,desc`")
				),
				responseFields(apiResponseFields(combine(
					fields(
						fieldWithPath("data.campusId").description("캠퍼스 ID"),
						fieldWithPath("data.campusName").description("캠퍼스명"),
						fieldWithPath("data.region").optional().description("캠퍼스 지역")
					),
					chargeAmountSummaryFields("data.summary."),
					fields(fieldWithPath("data.items[]").description("청구 항목 목록")),
					chargeListItemFields("data.items[].")
				)))
			));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me/summary", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("year", "2026")
				.param("month", "6"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andDo(document("charge-my-summary-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("납부 요약을 조회할 캠퍼스 ID")),
				queryParameters(
					parameterWithName("year").description("조회 연도"),
					parameterWithName("month").description("조회 월")
				),
				responseFields(apiResponseFields(
					fieldWithPath("data.campusId").description("캠퍼스 ID"),
					fieldWithPath("data.campusName").description("캠퍼스명"),
					fieldWithPath("data.region").optional().description("캠퍼스 지역"),
					fieldWithPath("data.userId").description("사용자 ID"),
					fieldWithPath("data.name").description("사용자 이름"),
					fieldWithPath("data.totalPaidAmount").description("전체 납부 완료 금액"),
					fieldWithPath("data.monthlyPaidAmount").description("선택 월에 납부 완료된 금액. `paidAt` 기준"),
					fieldWithPath("data.monthlyUnpaidAmount").description("선택 월에 청구 생성된 미납 금액. `createdAt` 기준"),
					fieldWithPath("data.monthlyTotalChargeAmount").description("선택 월에 청구 생성된 전체 청구 금액. `createdAt` 기준"),
					fieldWithPath("data.monthlyByCategory[]").description("선택 월 청구 유형별 집계"),
					fieldWithPath("data.monthlyByCategory[].paymentCategory").description("청구 유형"),
					fieldWithPath("data.monthlyByCategory[].paidAmount").description("해당 월 생성 청구 중 납부 완료 금액"),
					fieldWithPath("data.monthlyByCategory[].unpaidAmount").description("해당 월 생성 청구 중 미납 금액"),
					fieldWithPath("data.monthlyByCategory[].totalAmount").description("해당 월 생성 청구 중 전체 금액")
				))
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/charges", campusId)
				.header("Authorization", "Bearer " + managerToken)
				.param("status", "UNPAID")
				.param("keyword", "docs-billing-query-member")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.members[0].items").doesNotExist())
			.andDo(document("charge-admin-campus-summary-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("청구 집계를 조회할 캠퍼스 ID")),
				queryParameters(
					parameterWithName("paymentCategory").optional().description("청구 유형 필터. `PENALTY` 또는 `COFFEE`"),
					parameterWithName("status").optional().description("청구 상태 필터. `UNPAID`, `PAID`, `WAIVED`, `CANCELED`"),
					parameterWithName("userId").optional().description("사용자 ID 필터"),
					parameterWithName("keyword").optional().description("이름 또는 이메일 검색어"),
					parameterWithName("page").optional().description("페이지 번호. 기본 0"),
					parameterWithName("size").optional().description("페이지 크기. 기본 20, 최대 100"),
					parameterWithName("sort").optional().description("정렬. 기본 `createdAt,desc`")
				),
				responseFields(apiResponseFields(combine(
					fields(
						fieldWithPath("data.campusId").description("캠퍼스 ID"),
						fieldWithPath("data.campusName").description("캠퍼스명"),
						fieldWithPath("data.region").optional().description("캠퍼스 지역")
					),
					chargeAmountSummaryFields("data.summary."),
					fields(
						fieldWithPath("data.members[]").description("회원별 청구 집계 목록. 개별 청구 item 목록은 포함하지 않음"),
						fieldWithPath("data.members[].userId").description("사용자 ID"),
						fieldWithPath("data.members[].name").description("사용자 이름"),
						fieldWithPath("data.members[].email").description("사용자 이메일"),
						fieldWithPath("data.members[].totalAmount").description("회원별 전체 청구 금액"),
						fieldWithPath("data.members[].unpaidAmount").description("회원별 미납 금액"),
						fieldWithPath("data.members[].paidAmount").description("회원별 납부 완료 금액"),
						fieldWithPath("data.members[].waivedAmount").description("회원별 면제 금액"),
						fieldWithPath("data.members[].canceledAmount").description("회원별 취소 금액")
					)
				)))
			));

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/members/{userId}/charges", campusId, member.id())
				.header("Authorization", "Bearer " + managerToken)
				.param("paymentCategory", "PENALTY")
				.param("page", "0")
				.param("size", "20")
				.param("sort", "createdAt,desc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.userId").value(member.id()))
			.andDo(document("charge-admin-member-detail-success",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("청구 상세를 조회할 캠퍼스 ID"),
					parameterWithName("userId").description("청구 대상 사용자 ID")
				),
				queryParameters(
					parameterWithName("paymentCategory").optional().description("청구 유형 필터. `PENALTY` 또는 `COFFEE`"),
					parameterWithName("status").optional().description("청구 상태 필터. `UNPAID`, `PAID`, `WAIVED`, `CANCELED`"),
					parameterWithName("page").optional().description("페이지 번호. 기본 0"),
					parameterWithName("size").optional().description("페이지 크기. 기본 20, 최대 100"),
					parameterWithName("sort").optional().description("정렬. 기본 `createdAt,desc`")
				),
				responseFields(apiResponseFields(combine(
					fields(
						fieldWithPath("data.campusId").description("캠퍼스 ID"),
						fieldWithPath("data.campusName").description("캠퍼스명"),
						fieldWithPath("data.region").optional().description("캠퍼스 지역"),
						fieldWithPath("data.userId").description("청구 대상 사용자 ID"),
						fieldWithPath("data.name").description("청구 대상 사용자 이름"),
						fieldWithPath("data.email").description("청구 대상 사용자 이메일")
					),
					chargeAmountSummaryFields("data.summary."),
					fields(fieldWithPath("data.items[]").description("회원별 청구 항목 목록")),
					chargeListItemFields("data.items[].")
				)))
			));
	}

	@Test
	void documents_error_response_contract_with_detailed_code() throws Exception {
		String managerToken = signupAndLogin("docs-billing-error-manager@example.com", UserRole.MANAGER);
		User manager = userRepository.findByEmail("docs-billing-error-manager@example.com").orElseThrow();
		JsonNode campus = createCampus(managerToken, "51캠");
		long campusId = campus.path("campusId").asLong();
		String memberToken = signupAndLogin("docs-billing-error-member@example.com", UserRole.USER);
		User member = userRepository.findByEmail("docs-billing-error-member@example.com").orElseThrow();
		joinCampus(memberToken, campus.path("inviteCode").asText());
		createPenaltyAccount(campusId, manager.id(), "123-456789-022");
		createPenaltyCharge(campusId, member.id(), 7201L);

		mockMvc.perform(get("/api/v1/campuses/{campusId}/charges/me", campusId)
				.header("Authorization", "Bearer " + memberToken)
				.param("sort", "createdAt,wrong"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("BILLING_INVALID_SORT_DIRECTION"))
			.andExpect(jsonPath("$.message").value("지원하지 않는 정렬 방향입니다."))
			.andDo(document("error-response-detailed-code",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authHeader(),
				pathParameters(parameterWithName("campusId").description("청구 목록을 조회할 캠퍼스 ID")),
				queryParameters(parameterWithName("sort").description("잘못된 정렬 방향 예시")),
				responseFields(errorResponseFields())
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

	private static FieldDescriptor[] errorResponseFields() {
		return new FieldDescriptor[] {
			fieldWithPath("success").description("요청 성공 여부. 오류 응답에서는 `false`"),
			fieldWithPath("code").description("HTTP status와 함께 고정 계약으로 사용하는 세부 오류 코드"),
			fieldWithPath("message").description("사용자 표시용 오류 메시지"),
			fieldWithPath("data").type(JsonFieldType.NULL).description("오류 응답에서는 null"),
			fieldWithPath("timestamp").description("응답 생성 시각")
		};
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

	private static FieldDescriptor[] chargeFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "id").description("청구 항목 ID"),
			fieldWithPath(prefix + "campusId").description("캠퍼스 ID"),
			fieldWithPath(prefix + "userId").description("청구 대상 사용자 ID"),
			fieldWithPath(prefix + "paymentCategory").description("청구 유형. `PENALTY` 또는 `COFFEE`"),
			fieldWithPath(prefix + "title").description("청구 제목"),
			fieldWithPath(prefix + "reason").optional().description("청구 사유"),
			fieldWithPath(prefix + "amount").description("청구 금액"),
			fieldWithPath(prefix + "status").description("청구 상태"),
			fieldWithPath(prefix + "paidAt").type(JsonFieldType.STRING).optional().description("납부 완료 시각. 미납, 면제, 취소 상태에서는 없거나 null")
		};
	}

	private static FieldDescriptor[] chargeAmountSummaryFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "totalAmount").description("전체 청구 금액"),
			fieldWithPath(prefix + "unpaidAmount").description("미납 금액"),
			fieldWithPath(prefix + "paidAmount").description("납부 완료 금액"),
			fieldWithPath(prefix + "waivedAmount").description("면제 금액"),
			fieldWithPath(prefix + "canceledAmount").description("취소 금액")
		};
	}

	private static FieldDescriptor[] chargeListItemFields(String prefix) {
		return new FieldDescriptor[] {
			fieldWithPath(prefix + "id").description("청구 항목 ID"),
			fieldWithPath(prefix + "paymentCategory").description("청구 유형. `PENALTY` 또는 `COFFEE`"),
			fieldWithPath(prefix + "title").description("청구 제목"),
			fieldWithPath(prefix + "reason").optional().description("청구 사유"),
			fieldWithPath(prefix + "amount").description("청구 금액"),
			fieldWithPath(prefix + "status").description("청구 상태"),
			fieldWithPath(prefix + "dueDate").optional().description("납부 기한"),
			fieldWithPath(prefix + "paidAt").type(JsonFieldType.STRING).optional().description("납부 완료 시각"),
			fieldWithPath(prefix + "account.paymentAccountId").description("연결된 납부 계좌 ID"),
			fieldWithPath(prefix + "account.bankName").description("은행명 snapshot"),
			fieldWithPath(prefix + "account.accountNumber").description("계좌번호 snapshot"),
			fieldWithPath(prefix + "account.accountHolder").description("예금주 snapshot"),
			fieldWithPath(prefix + "source.sourceType").description("청구 생성 출처 유형"),
			fieldWithPath(prefix + "source.sourceId").description("청구 생성 출처 ID")
		};
	}

	private static FieldDescriptor[] fields(FieldDescriptor... descriptors) {
		return descriptors;
	}

	private static FieldDescriptor[] combine(FieldDescriptor[]... groups) {
		int size = 0;
		for (FieldDescriptor[] group : groups) {
			size += group.length;
		}
		FieldDescriptor[] combined = new FieldDescriptor[size];
		int index = 0;
		for (FieldDescriptor[] group : groups) {
			System.arraycopy(group, 0, combined, index, group.length);
			index += group.length;
		}
		return combined;
	}
}
