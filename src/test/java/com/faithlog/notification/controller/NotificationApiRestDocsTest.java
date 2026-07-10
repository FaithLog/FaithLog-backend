package com.faithlog.notification.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.notification.service.FcmTokenService;
import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
@ActiveProfiles("test")
@Transactional
class NotificationApiRestDocsTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Test
	void registerFcmTokenDocs() throws Exception {
		String accessToken = signupAndLogin("notification-docs-fcm@example.com", UserRole.USER);

		mockMvc.perform(post("/api/v1/users/me/fcm-tokens")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "token": "docs-fcm-token",
					  "clientInstanceId": "docs-client-instance",
					  "deviceType": "IOS",
					  "appVersion": "1.0.0"
					}
					"""))
			.andExpect(status().isOk())
			.andDo(document(
				"notification-register-fcm-token",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authorizationHeader(),
				requestFields(
					fieldWithPath("token").type(JsonFieldType.STRING).description("프론트 Firebase SDK가 발급/조회한 FCM 토큰"),
					fieldWithPath("clientInstanceId").type(JsonFieldType.STRING).description("앱 설치 단위 식별값"),
					fieldWithPath("deviceType").type(JsonFieldType.STRING).description("기기 유형: ANDROID, IOS, WEB"),
					fieldWithPath("appVersion").type(JsonFieldType.STRING).optional().description("앱 버전")
				),
				responseFields(successEnvelopeData(
					fieldWithPath("data.tokenId").type(JsonFieldType.NUMBER).description("등록 또는 갱신된 FCM 토큰 ID"),
					fieldWithPath("data.token").type(JsonFieldType.STRING).description("등록 또는 갱신된 FCM 토큰"),
					fieldWithPath("data.deviceType").type(JsonFieldType.STRING).description("기기 유형"),
					fieldWithPath("data.clientInstanceId").type(JsonFieldType.STRING).description("앱 설치 단위 식별값"),
					fieldWithPath("data.appVersion").type(JsonFieldType.STRING).optional().description("앱 버전"),
					fieldWithPath("data.isActive").type(JsonFieldType.BOOLEAN).description("활성 여부"),
					fieldWithPath("data.lastSeenAt").type(JsonFieldType.STRING).description("마지막 확인 시각"),
					fieldWithPath("data.lastRefreshedAt").type(JsonFieldType.STRING).description("마지막 갱신 시각")
				))
			));
	}

	@Test
	void deactivateFcmTokenDocs() throws Exception {
		String accessToken = signupAndLogin("notification-docs-fcm-delete@example.com", UserRole.USER);
		User user = userRepository.findByEmail("notification-docs-fcm-delete@example.com").orElseThrow();
		Long tokenId = fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			"docs-delete-token",
			"docs-delete-client",
			DeviceType.ANDROID,
			"1.0.0"
		)).id();

		mockMvc.perform(delete("/api/v1/users/me/fcm-tokens/{tokenId}", tokenId)
				.header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isNoContent())
			.andDo(document(
				"notification-deactivate-fcm-token",
				preprocessRequest(prettyPrint()),
				authorizationHeader(),
				pathParameters(parameterWithName("tokenId").description("비활성화할 본인 소유 FCM 토큰 ID"))
			));
	}

	@Test
	void sendAdminNotificationDocs() throws Exception {
		Fixture fixture = notificationFixture("notification-docs-send");

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/notifications", fixture.campusId())
				.header("Authorization", "Bearer " + fixture.ministerToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "notificationType": "CUSTOM",
					  "targetUserIds": [%d],
					  "targetWeekStartDate": null,
					  "targetId": null,
					  "title": "경건생활 제출 알림",
					  "body": "이번 주 경건생활을 제출해 주세요."
					}
					""".formatted(fixture.targetUserId())))
			.andExpect(status().isAccepted())
			.andDo(document(
				"notification-send-admin-notification",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authorizationHeader(),
				pathParameters(parameterWithName("campusId").description("알림을 발송할 캠퍼스 ID")),
				requestFields(
					fieldWithPath("notificationType").type(JsonFieldType.STRING).description("알림 유형"),
					fieldWithPath("targetUserIds").type(JsonFieldType.ARRAY).optional().description("지정 발송 대상 사용자 ID 목록"),
					fieldWithPath("targetWeekStartDate").type(JsonFieldType.NULL).optional().description("대상 주차 시작일"),
					fieldWithPath("targetId").type(JsonFieldType.NULL).optional().description("투표 등 대상 리소스 ID"),
					fieldWithPath("title").type(JsonFieldType.STRING).description("알림 제목"),
					fieldWithPath("body").type(JsonFieldType.STRING).description("알림 본문")
				),
				responseFields(successEnvelopeData(
					fieldWithPath("data.notificationRequestId").type(JsonFieldType.STRING).description("notification_logs.request_id 값"),
					fieldWithPath("data.queuedCount").type(JsonFieldType.NUMBER).description("PENDING으로 큐잉된 대상 수"),
					fieldWithPath("data.skippedCount").type(JsonFieldType.NUMBER).description("토큰 없음 등으로 SKIPPED 처리된 대상 수")
				))
			));
	}

	@Test
	void listNotificationLogsDocs() throws Exception {
		Fixture fixture = notificationFixture("notification-docs-logs");
		JsonNode sendResponse = sendCustomNotification(fixture);
		String requestId = sendResponse.path("notificationRequestId").asText();

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/notification-logs", fixture.campusId())
				.header("Authorization", "Bearer " + fixture.ministerToken())
				.param("requestId", requestId))
			.andExpect(status().isOk())
			.andDo(document(
				"notification-list-notification-logs",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				authorizationHeader(),
				pathParameters(parameterWithName("campusId").description("알림 로그를 조회할 캠퍼스 ID")),
				queryParameters(
					parameterWithName("notificationType").optional().description("알림 유형 필터"),
					parameterWithName("sendStatus").optional().description("발송 상태 필터"),
					parameterWithName("targetWeekStartDate").optional().description("대상 주차 필터"),
					parameterWithName("targetId").optional().description("대상 리소스 ID 필터"),
					parameterWithName("requestId").optional().description("발송 요청 묶음 ID 필터"),
					parameterWithName("startDate").optional().description("조회 시작일"),
					parameterWithName("endDate").optional().description("조회 종료일"),
					parameterWithName("page").optional().description("페이지 번호"),
					parameterWithName("size").optional().description("페이지 크기"),
					parameterWithName("sort").optional().description("정렬 조건")
				),
				responseFields(successEnvelopeData(
					fieldWithPath("data.items[].notificationLogId").type(JsonFieldType.NUMBER).description("알림 로그 ID"),
					fieldWithPath("data.items[].requestId").type(JsonFieldType.STRING).description("발송 요청 묶음 ID"),
					fieldWithPath("data.items[].userId").type(JsonFieldType.NUMBER).description("대상 사용자 ID"),
					fieldWithPath("data.items[].name").type(JsonFieldType.STRING).description("대상 사용자 이름"),
					fieldWithPath("data.items[].email").type(JsonFieldType.STRING).description("대상 사용자 이메일"),
					fieldWithPath("data.items[].campusId").type(JsonFieldType.NUMBER).description("캠퍼스 ID"),
					fieldWithPath("data.items[].notificationType").type(JsonFieldType.STRING).description("알림 유형"),
					fieldWithPath("data.items[].targetWeekStartDate").type(JsonFieldType.NULL).optional().description("대상 주차 시작일"),
					fieldWithPath("data.items[].targetId").type(JsonFieldType.NULL).optional().description("대상 리소스 ID"),
					fieldWithPath("data.items[].title").type(JsonFieldType.STRING).description("알림 제목"),
					fieldWithPath("data.items[].body").type(JsonFieldType.STRING).description("알림 본문"),
					fieldWithPath("data.items[].sendStatus").type(JsonFieldType.STRING).description("발송 상태"),
					fieldWithPath("data.items[].failureReason").type(JsonFieldType.NULL).optional().description("실패 또는 스킵 사유"),
					fieldWithPath("data.items[].sentAt").type(JsonFieldType.NULL).optional().description("발송 성공 시각"),
					fieldWithPath("data.items[].createdAt").type(JsonFieldType.STRING).description("로그 생성 시각"),
					fieldWithPath("data.page").type(JsonFieldType.NUMBER).description("현재 페이지"),
					fieldWithPath("data.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
					fieldWithPath("data.totalElements").type(JsonFieldType.NUMBER).description("전체 항목 수"),
					fieldWithPath("data.totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수")
				))
			));
	}

	private Fixture notificationFixture(String prefix) throws Exception {
		String ministerToken = signupAndLogin(prefix + "-minister@example.com", UserRole.USER);
		User minister = userRepository.findByEmail(prefix + "-minister@example.com").orElseThrow();
		User target = saveUser(prefix + "-target@example.com", UserRole.USER);
		Campus campus = saveCampus(prefix + "-campus");
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), target.id());
		fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			target.id(),
			prefix + "-fcm-token",
			prefix + "-client",
			DeviceType.IOS,
			"1.0.0"
		));
		return new Fixture(ministerToken, campus.id(), target.id());
	}

	private JsonNode sendCustomNotification(Fixture fixture) throws Exception {
		String body = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/notifications", fixture.campusId())
				.header("Authorization", "Bearer " + fixture.ministerToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "notificationType": "CUSTOM",
					  "targetUserIds": [%d],
					  "title": "공지",
					  "body": "알림 본문"
					}
					""".formatted(fixture.targetUserId())))
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private Campus saveCampus(String name) {
		return campusRepository.saveAndFlush(Campus.create(name, "분당", name, "INV-" + name));
	}

	private CampusMember saveMinister(Long campusId, Long userId) {
		return campusMemberRepository.saveAndFlush(CampusMember.createMinister(campusId, userId));
	}

	private CampusMember saveMember(Long campusId, Long userId) {
		return campusMemberRepository.saveAndFlush(CampusMember.createMember(campusId, userId));
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.save(User.create(email.substring(0, email.indexOf('@')), email, "{noop}password"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private String signupAndLogin(String email, UserRole role) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "알림문서테스트",
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

	private org.springframework.restdocs.snippet.Snippet authorizationHeader() {
		return requestHeaders(headerWithName("Authorization").description("`Bearer {accessToken}` 형식의 Access Token"));
	}

	private List<org.springframework.restdocs.payload.FieldDescriptor> successEnvelopeData(
		org.springframework.restdocs.payload.FieldDescriptor... dataFields
	) {
		List<org.springframework.restdocs.payload.FieldDescriptor> descriptors = new ArrayList<>(Arrays.asList(
			fieldWithPath("success").type(JsonFieldType.BOOLEAN).description("성공 여부"),
			fieldWithPath("code").type(JsonFieldType.STRING).description("응답 코드"),
			fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
			fieldWithPath("timestamp").type(JsonFieldType.STRING).description("응답 시각")
		));
		descriptors.addAll(Arrays.asList(dataFields));
		return descriptors;
	}

	private record Fixture(String ministerToken, Long campusId, Long targetUserId) {
	}
}
