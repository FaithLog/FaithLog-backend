package com.faithlog.notification.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.entity.CampusDutyAssignment;
import com.faithlog.campus.infrastructure.repository.CampusDutyAssignmentRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.notification.service.FcmTokenService;
import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.support.NotificationConcurrencyTestConfig;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

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

	@Autowired
	private CampusDutyAssignmentRepository campusDutyAssignmentRepository;

	@Autowired
	private PaymentAccountRepository paymentAccountRepository;

	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@Autowired
	private NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort notificationConcurrencyPort;

	@BeforeEach
	void resetNotificationConcurrencyPort() {
		notificationConcurrencyPort.reset();
	}

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

	@Test
	void admin_notification_send_fails_when_redis_lock_is_unavailable() throws Exception {
		String ministerToken = signupAndLogin("notification-http-redis-minister@example.com", UserRole.USER);
		User minister = userRepository.findByEmail("notification-http-redis-minister@example.com").orElseThrow();
		User target = saveUser("notification-http-redis-target@example.com", UserRole.USER);
		Campus campus = saveCampus("알림HTTP캠C");
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), target.id());
		registerToken(target, "notification-http-redis-token", "notification-http-redis-client");
		notificationConcurrencyPort.fail();

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/notifications", campus.id())
				.header("Authorization", "Bearer " + ministerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "notificationType": "CUSTOM",
					  "targetUserIds": [%d],
					  "title": "공지",
					  "body": "알림 본문"
					}
					""".formatted(target.id())))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_REDIS_UNAVAILABLE"));

		assertThat(notificationLogRepository.count()).isZero();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	void coffee_charge_reminder_sends_all_owned_unpaid_total_once_per_account_and_recipient_daily() throws Exception {
		String dutyToken = signupAndLogin("notification-200-coffee-duty@example.com", UserRole.USER);
		User duty = userRepository.findByEmail("notification-200-coffee-duty@example.com").orElseThrow();
		User target = saveUser("notification-200-coffee-target@example.com", UserRole.USER);
		User otherDuty = saveUser("notification-200-coffee-other-duty@example.com", UserRole.USER);
		Campus campus = saveCampus("알림200커피캠");
		Campus otherCampus = saveCampus("알림200다른캠");
		saveMember(campus.id(), duty.id());
		saveMember(campus.id(), target.id());
		saveMember(campus.id(), otherDuty.id());
		campusDutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignCoffee(campus.id(), duty.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.id(), PaymentCategory.COFFEE, "담당자 커피 계좌", "하나은행", "200-COFFEE", "커피담당", duty.id()
		));
		chargeItemRepository.saveAndFlush(charge(campus.id(), target.id(), account, 20011L, "아이스 아메리카노", 1800));
		chargeItemRepository.saveAndFlush(charge(campus.id(), target.id(), account, 20012L, "카페라떼", 2200));
		ChargeItem paid = charge(campus.id(), target.id(), account, 20013L, "결제 완료 메뉴", 5000);
		paid.markPaid();
		chargeItemRepository.saveAndFlush(paid);
		PaymentAccount otherOwnerAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.id(), PaymentCategory.COFFEE, "다른 담당자 계좌", "국민은행", "200-OTHER", "다른담당", otherDuty.id()
		));
		PaymentAccount penaltyAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.id(), PaymentCategory.PENALTY, "벌금 계좌", "신한은행", "200-PENALTY", "벌금담당", duty.id()
		));
		PaymentAccount otherCampusAccount = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			otherCampus.id(), PaymentCategory.COFFEE, "다른 캠퍼스 계좌", "농협은행", "200-OTHER-CAMPUS", "커피담당", duty.id()
		));
		chargeItemRepository.saveAndFlush(charge(campus.id(), target.id(), otherOwnerAccount, 20014L, "다른 담당자 메뉴", 9000));
		chargeItemRepository.saveAndFlush(charge(campus.id(), target.id(), penaltyAccount, 20015L, "벌금", 8000));
		chargeItemRepository.saveAndFlush(charge(otherCampus.id(), target.id(), otherCampusAccount, 20016L, "다른 캠퍼스 메뉴", 7000));
		registerToken(target, "notification-200-coffee-token", "notification-200-coffee-client");

		String firstBody = mockMvc.perform(post("/api/v1/campuses/{campusId}/coffee/charge-reminders", campus.id())
				.header("Authorization", "Bearer " + dutyToken))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.notificationRequestId").isString())
			.andExpect(jsonPath("$.data.queuedCount").value(1))
			.andExpect(jsonPath("$.data.skippedCount").value(0))
			.andReturn().getResponse().getContentAsString();
		String requestId = objectMapper.readTree(firstBody).path("data").path("notificationRequestId").asText();
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(java.util.UUID.fromString(requestId)))
			.singleElement()
			.satisfies(log -> {
				assertThat(log.title()).isEqualTo("커피 미납 청구 안내");
				assertThat(log.body()).isEqualTo(
					"커피 미납: 아이스 아메리카노 1건 1800원, 카페라떼 1건 2200원 / 총 4000원입니다. 확인 후 납부해 주세요."
				);
				assertThat(log.userId()).isEqualTo(target.id());
			});

		mockMvc.perform(post("/api/v1/campuses/{campusId}/coffee/charge-reminders", campus.id())
				.header("Authorization", "Bearer " + dutyToken))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.queuedCount").value(0))
			.andExpect(jsonPath("$.data.skippedCount").value(1));
		assertThat(notificationLogRepository.count()).isEqualTo(1);
	}

	@Test
	void meal_charge_reminder_uses_meal_scope_and_non_duty_manager_is_forbidden() throws Exception {
		String dutyToken = signupAndLogin("notification-200-meal-duty@example.com", UserRole.USER);
		String managerToken = signupAndLogin("notification-200-manager@example.com", UserRole.USER);
		User duty = userRepository.findByEmail("notification-200-meal-duty@example.com").orElseThrow();
		User manager = userRepository.findByEmail("notification-200-manager@example.com").orElseThrow();
		User target = saveUser("notification-200-meal-target@example.com", UserRole.USER);
		Campus campus = saveCampus("알림200밥캠");
		saveMember(campus.id(), duty.id());
		saveMinister(campus.id(), manager.id());
		saveMember(campus.id(), target.id());
		campusDutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignMeal(campus.id(), duty.id()));
		PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
			campus.id(), PaymentCategory.MEAL, "담당자 밥 계좌", "국민은행", "200-MEAL", "밥담당", duty.id()
		));
		chargeItemRepository.saveAndFlush(charge(campus.id(), target.id(), account, 20021L, "주일 점심", 7000));
		registerToken(target, "notification-200-meal-token", "notification-200-meal-client");

		String responseBody = mockMvc.perform(post("/api/v1/campuses/{campusId}/meal/charge-reminders", campus.id())
				.header("Authorization", "Bearer " + dutyToken))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.queuedCount").value(1))
			.andExpect(jsonPath("$.data.skippedCount").value(0))
			.andReturn().getResponse().getContentAsString();
		String requestId = objectMapper.readTree(responseBody).path("data").path("notificationRequestId").asText();
		assertThat(notificationLogRepository.findByRequestIdOrderByIdAsc(java.util.UUID.fromString(requestId)))
			.singleElement()
			.satisfies(log -> {
				assertThat(log.title()).isEqualTo("밥 미납 청구 안내");
				assertThat(log.body()).isEqualTo(
					"밥 미납: 주일 점심 1건 7000원 / 총 7000원입니다. 확인 후 납부해 주세요."
				);
			});

		mockMvc.perform(post("/api/v1/campuses/{campusId}/meal/charge-reminders", campus.id())
				.header("Authorization", "Bearer " + managerToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void charge_reminder_fails_without_creating_logs_when_redis_is_unavailable() throws Exception {
		String dutyToken = signupAndLogin("notification-200-redis-duty@example.com", UserRole.USER);
		User duty = userRepository.findByEmail("notification-200-redis-duty@example.com").orElseThrow();
		Campus campus = saveCampus("알림200Redis캠");
		saveMember(campus.id(), duty.id());
		campusDutyAssignmentRepository.saveAndFlush(CampusDutyAssignment.assignCoffee(campus.id(), duty.id()));
		notificationConcurrencyPort.fail();

		mockMvc.perform(post("/api/v1/campuses/{campusId}/coffee/charge-reminders", campus.id())
				.header("Authorization", "Bearer " + dutyToken))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.code").value("NOTIFICATION_REDIS_UNAVAILABLE"));
		assertThat(notificationLogRepository.count()).isZero();
	}

	private ChargeItem charge(
		Long campusId,
		Long userId,
		PaymentAccount account,
		Long sourceId,
		String title,
		int amount
	) {
		return ChargeItem.create(
			campusId, userId, account.accountType(), account.id(), account.bankName(), account.accountNumber(),
			account.accountHolder(), ChargeSourceType.POLL_RESPONSE, sourceId, title, "미납", amount, null
		);
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
