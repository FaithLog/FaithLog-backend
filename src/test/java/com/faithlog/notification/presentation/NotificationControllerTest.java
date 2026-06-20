package com.faithlog.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.campus.infrastructure.jpa.CampusRepository;
import com.faithlog.notification.application.FcmTokenService;
import com.faithlog.notification.application.RegisterFcmTokenCommand;
import com.faithlog.notification.domain.DeviceType;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Test
	void admin_notification_send_returns_accepted_and_creates_pending_logs() throws Exception {
		String ministerToken = signupAndLogin("notification-http-minister@example.com", UserRole.USER);
		User minister = userRepository.findByEmail("notification-http-minister@example.com").orElseThrow();
		User withToken = saveUser("notification-http-target@example.com", UserRole.USER);
		User withoutToken = saveUser("notification-http-skipped@example.com", UserRole.USER);
		Campus campus = saveCampus("알림HTTP캠A");
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), withToken.id());
		saveMember(campus.id(), withoutToken.id());
		registerToken(withToken, "notification-http-token", "notification-http-client");

		String body = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/notifications", campus.id())
				.header("Authorization", "Bearer " + ministerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "notificationType": "CUSTOM",
					  "targetUserIds": [%d, %d],
					  "targetWeekStartDate": null,
					  "targetId": null,
					  "title": "공지",
					  "body": "알림 본문"
					}
					""".formatted(withToken.id(), withoutToken.id())))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.notificationRequestId").isString())
			.andExpect(jsonPath("$.data.queuedCount").value(1))
			.andExpect(jsonPath("$.data.skippedCount").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String requestId = objectMapper.readTree(body).path("data").path("notificationRequestId").asText();

		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(java.util.UUID.fromString(requestId))).hasSize(2);
	}

	@Test
	void notification_logs_query_filters_by_request_id_and_returns_user_identity() throws Exception {
		String ministerToken = signupAndLogin("notification-http-log-minister@example.com", UserRole.USER);
		User minister = userRepository.findByEmail("notification-http-log-minister@example.com").orElseThrow();
		User target = saveUser("notification-http-log-target@example.com", UserRole.USER);
		Campus campus = saveCampus("알림HTTP캠B");
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), target.id());
		registerToken(target, "notification-http-log-token", "notification-http-log-client");
		JsonNode sendResponse = sendCustomNotification(ministerToken, campus.id(), target.id());
		String requestId = sendResponse.path("notificationRequestId").asText();

		mockMvc.perform(get("/api/v1/admin/campuses/{campusId}/notification-logs", campus.id())
				.header("Authorization", "Bearer " + ministerToken)
				.param("requestId", requestId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].requestId").value(requestId))
			.andExpect(jsonPath("$.data.items[0].userId").value(target.id()))
			.andExpect(jsonPath("$.data.items[0].name").value(target.name()))
			.andExpect(jsonPath("$.data.items[0].email").value(target.email()))
			.andExpect(jsonPath("$.data.items[0].sendStatus").value("PENDING"));
	}

	private JsonNode sendCustomNotification(String accessToken, Long campusId, Long targetUserId) throws Exception {
		String body = mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/notifications", campusId)
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "notificationType": "CUSTOM",
					  "targetUserIds": [%d],
					  "title": "공지",
					  "body": "알림 본문"
					}
					""".formatted(targetUserId)))
			.andExpect(status().isAccepted())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return objectMapper.readTree(body).path("data");
	}

	private void registerToken(User user, String token, String clientInstanceId) {
		fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			token,
			clientInstanceId,
			DeviceType.IOS,
			"1.0.0"
		));
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
					  "name": "알림HTTP테스트",
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
