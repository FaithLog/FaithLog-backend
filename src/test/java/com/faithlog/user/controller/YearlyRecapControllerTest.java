package com.faithlog.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.service.policy.YearlyRecapPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class YearlyRecapControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private YearlyRecapPolicy yearlyRecapPolicy;

	@Test
	void no_data_returns_an_exact_zero_payload_without_private_fields() throws Exception {
		Tokens tokens = signupAndLogin("recap-zero@example.com");
		int recapYear = yearlyRecapPolicy.previousPeriod().recapYear();

		mockMvc.perform(get("/api/v1/users/me/yearly-recaps/previous")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.recapYear").value(recapYear))
			.andExpect(jsonPath("$.data.hasRecapData").value(false))
			.andExpect(jsonPath("$.data.presentation.shouldAutoPresent").value(false))
			.andExpect(jsonPath("$.data.presentation.homeCardVisible").value(false))
			.andExpect(jsonPath("$.data.presentation.firstPresentedAt").doesNotExist())
			.andExpect(jsonPath("$.data.campusJourney.campuses").isEmpty())
			.andExpect(jsonPath("$.data.devotion.quietTimeCount").value(0))
			.andExpect(jsonPath("$.data.devotion.bibleReadingCount").value(0))
			.andExpect(jsonPath("$.data.devotion.prayerCount").value(0))
			.andExpect(jsonPath("$.data.devotion.allCompletedDayCount").value(0))
			.andExpect(jsonPath("$.data.devotion.submittedWeekCount").value(0))
			.andExpect(jsonPath("$.data.devotion.longestStreakDays").value(0))
			.andExpect(jsonPath("$.data.devotion.mostActiveMonth").doesNotExist())
			.andExpect(jsonPath("$.data.prayerActivity.submittedWeekCount").value(0))
			.andExpect(jsonPath("$.data.prayerActivity.participatedSeasonCount").value(0))
			.andExpect(jsonPath("$.data.pollActivity.participatedCount").value(0))
			.andExpect(jsonPath("$.data.pollActivity.wedServicePollCount").value(0))
			.andExpect(jsonPath("$.data.pollActivity.saturdayLeaderPollCount").value(0))
			.andExpect(jsonPath("$.data.pollActivity.coffeePollCount").value(0))
			.andExpect(jsonPath("$.data.pollActivity.mealPollCount").value(0))
			.andExpect(jsonPath("$.data.pollActivity.customPollCount").value(0))
			.andExpect(jsonPath("$.data.pollActivity.commentCount").value(0))
			.andExpect(jsonPath("$.data.email").doesNotExist())
			.andExpect(jsonPath("$.data.prayerContent").doesNotExist())
			.andExpect(jsonPath("$.data.pollSelection").doesNotExist())
			.andExpect(jsonPath("$.data.commentContent").doesNotExist())
			.andExpect(jsonPath("$.data.accountNumber").doesNotExist())
			.andExpect(jsonPath("$.data.amount").doesNotExist())
			.andExpect(jsonPath("$.data.fcmToken").doesNotExist());
	}

	@Test
	void valid_no_data_presented_is_an_idempotent_no_op() throws Exception {
		Tokens tokens = signupAndLogin("recap-noop@example.com");
		User user = userRepository.findByEmail("recap-noop@example.com").orElseThrow();
		int recapYear = yearlyRecapPolicy.previousPeriod().recapYear();

		mockMvc.perform(get("/api/v1/users/me/yearly-recaps/previous")
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isOk());

		for (int call = 0; call < 2; call++) {
			mockMvc.perform(post("/api/v1/users/me/yearly-recaps/{recapYear}/presented", recapYear)
					.header("Authorization", "Bearer " + tokens.accessToken()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").doesNotExist());
		}

		Integer presentedCount = jdbcTemplate.queryForObject("""
			select count(*)
			from yearly_recap_snapshots
			where user_id = ? and recap_year = ? and first_presented_at is not null
			""", Integer.class, user.id(), recapYear);
		assertThat(presentedCount).isZero();
	}

	@Test
	void presented_rejects_a_year_other_than_the_current_previous_year() throws Exception {
		Tokens tokens = signupAndLogin("recap-invalid-year@example.com");
		int invalidYear = yearlyRecapPolicy.previousPeriod().recapYear() - 1;

		mockMvc.perform(post("/api/v1/users/me/yearly-recaps/{recapYear}/presented", invalidYear)
				.header("Authorization", "Bearer " + tokens.accessToken()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.code").value("USER_YEARLY_RECAP_INVALID_YEAR"));
	}

	@Test
	void endpoints_require_an_access_token_and_reject_a_refresh_bearer() throws Exception {
		Tokens tokens = signupAndLogin("recap-auth@example.com");
		int recapYear = yearlyRecapPolicy.previousPeriod().recapYear();

		mockMvc.perform(get("/api/v1/users/me/yearly-recaps/previous"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		mockMvc.perform(get("/api/v1/users/me/yearly-recaps/previous")
				.header("Authorization", "Bearer " + tokens.refreshToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

		mockMvc.perform(post("/api/v1/users/me/yearly-recaps/{recapYear}/presented", recapYear))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
	}

	private Tokens signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/v1/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "name": "회고 사용자",
					  "email": "%s",
					  "password": "1234"
					}
					""".formatted(email)))
			.andExpect(status().isCreated());

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
		JsonNode data = objectMapper.readTree(body).path("data");
		return new Tokens(data.path("accessToken").asText(), data.path("refreshToken").asText());
	}

	private record Tokens(String accessToken, String refreshToken) {
	}
}
