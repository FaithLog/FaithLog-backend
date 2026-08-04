package com.faithlog.user.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faithlog.global.security.AccessTokenBlacklistChecker;
import com.faithlog.global.security.AccessTokenVersionChecker;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.SessionRevocationChecker;
import com.faithlog.user.service.YearlyRecapPresentationCommandService;
import com.faithlog.user.service.YearlyRecapQueryService;
import com.faithlog.user.service.result.CampusJourneyResult;
import com.faithlog.user.service.result.CommentActivityRecapResult;
import com.faithlog.user.service.result.DevotionRecapResult;
import com.faithlog.user.service.result.PenaltySummaryRecapResult;
import com.faithlog.user.service.result.PrayerActivityRecapResult;
import com.faithlog.user.service.result.YearlyRecapPresentationResult;
import com.faithlog.user.service.result.YearlyRecapResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(YearlyRecapController.class)
@AutoConfigureMockMvc
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class YearlyRecapApiRestDocsTest {

	@Autowired private MockMvc mockMvc;

	@MockitoBean private YearlyRecapQueryService queryService;
	@MockitoBean private YearlyRecapPresentationCommandService presentationCommandService;
	@MockitoBean private JwtProvider jwtProvider;
	@MockitoBean private AccessTokenBlacklistChecker accessTokenBlacklistChecker;
	@MockitoBean private AccessTokenVersionChecker accessTokenVersionChecker;
	@MockitoBean private SessionRevocationChecker sessionRevocationChecker;

	@Test
	void documents_previous_yearly_recap() throws Exception {
		when(queryService.getPrevious(anyLong())).thenReturn(sampleResult());

		mockMvc.perform(get("/api/v1/users/me/yearly-recaps/previous")
				.with(authentication(authToken())))
			.andExpect(status().isOk())
			.andDo(document("users-me-yearly-recap-previous",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				responseFields(
					fieldWithPath("success").description("요청 성공 여부"),
					fieldWithPath("code").description("공통 응답 코드"),
					fieldWithPath("message").description("응답 메시지"),
					fieldWithPath("data").description("서버가 계산하고 snapshot으로 고정한 직전 연도 회고"),
					fieldWithPath("data.recapYear").description("Asia/Seoul 현재 연도의 직전 연도"),
					fieldWithPath("data.hasRecapData").description("회고에 표시할 활동 또는 ACTIVE 캠퍼스 여정 존재 여부"),
					fieldWithPath("data.presentation.shouldAutoPresent").description("아직 실제 표시 완료 전인 경우의 자동 표시 여부"),
					fieldWithPath("data.presentation.homeCardVisible").description("1월 1일부터 14일까지 홈 카드 노출 여부"),
					fieldWithPath("data.presentation.homeCardVisibleUntil").description("Asia/Seoul 홈 카드 노출 종료 시각"),
					fieldWithPath("data.presentation.firstPresentedAt").optional().description("최초 실제 표시 완료 시각"),
					fieldWithPath("data.campusJourney.campuses").description("현재 ACTIVE 캠퍼스별 여정"),
					fieldWithPath("data.campusJourney.campuses[].campusId").description("캠퍼스 ID"),
					fieldWithPath("data.campusJourney.campuses[].campusName").description("snapshot 시점 캠퍼스 이름"),
					fieldWithPath("data.campusJourney.campuses[].joinedDate").description("campus_members.joined_at의 Asia/Seoul 날짜"),
					fieldWithPath("data.campusJourney.campuses[].joinedDuringRecapYear").description("회고 연도에 가입 또는 재가입했는지 여부"),
					fieldWithPath("data.devotion.quietTimeCount").description("큐티 완료 날짜 수"),
					fieldWithPath("data.devotion.bibleReadingCount").description("말씀 읽기 완료 날짜 수"),
					fieldWithPath("data.devotion.prayerCount").description("기도 완료 날짜 수"),
					fieldWithPath("data.devotion.allCompletedDayCount").description("세 항목을 모두 완료한 날짜 수"),
					fieldWithPath("data.devotion.submittedWeekCount").description("최종 제출 주차 수"),
					fieldWithPath("data.devotion.longestStreakDays").description("세 항목 모두 완료의 최장 연속일"),
					fieldWithPath("data.devotion.mostActiveMonth").optional().description("체크 합계가 가장 높은 가장 이른 월. 활동이 없으면 null"),
					fieldWithPath("data.prayerActivity.submittedWeekCount").description("기도 주차 기준 제출 주차 수"),
					fieldWithPath("data.prayerActivity.participatedSeasonCount").description("제출로 참여한 distinct 기도 시즌 수"),
					fieldWithPath("data.commentActivity.writtenCount").description("본인이 작성한 삭제되지 않은 댓글 수"),
					fieldWithPath("data.penaltySummary.totalCount").description("경건 벌금 PAID/UNPAID 총 건수"),
					fieldWithPath("data.penaltySummary.totalAmount").description("경건 벌금 PAID/UNPAID 총액"),
					fieldWithPath("data.penaltySummary.paidCount").description("납부 완료 경건 벌금 건수"),
					fieldWithPath("data.penaltySummary.paidAmount").description("납부 완료 경건 벌금 금액"),
					fieldWithPath("data.penaltySummary.unpaidCount").description("미납 경건 벌금 건수"),
					fieldWithPath("data.penaltySummary.unpaidAmount").description("미납 경건 벌금 금액"),
					fieldWithPath("timestamp").description("응답 생성 시각")
				)
			));
	}

	@Test
	void documents_idempotent_presented() throws Exception {
		mockMvc.perform(post("/api/v1/users/me/yearly-recaps/{recapYear}/presented", 2026)
				.with(authentication(authToken()))
				.with(csrf()))
			.andExpect(status().isOk())
			.andDo(document("users-me-yearly-recap-presented",
				preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()),
				pathParameters(parameterWithName("recapYear").description("현재 직전 회고 연도")),
				responseFields(
					fieldWithPath("success").description("요청 성공 여부"),
					fieldWithPath("code").description("공통 응답 코드"),
					fieldWithPath("message").description("응답 메시지"),
					fieldWithPath("data").optional().description("응답 본문 없음"),
					fieldWithPath("timestamp").description("응답 생성 시각")
				)
			));
	}

	private UsernamePasswordAuthenticationToken authToken() {
		AuthenticatedUser principal = new AuthenticatedUser(
			10L, "USER", "session", "jti", Instant.parse("2027-01-02T00:00:00Z")
		);
		return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
	}

	private YearlyRecapResult sampleResult() {
		return new YearlyRecapResult(
			2026,
			true,
			new YearlyRecapPresentationResult(
				true,
				true,
				OffsetDateTime.parse("2027-01-14T23:59:59+09:00"),
				null
			),
			List.of(new CampusJourneyResult(1L, "서울 캠퍼스", LocalDate.of(2026, 3, 10), true)),
			new DevotionRecapResult(210, 185, 230, 150, 40, 12, 8),
			new PrayerActivityRecapResult(22, 2),
			new CommentActivityRecapResult(6),
			new PenaltySummaryRecapResult(7, 21_000, 4, 12_000, 3, 9_000)
		);
	}
}
