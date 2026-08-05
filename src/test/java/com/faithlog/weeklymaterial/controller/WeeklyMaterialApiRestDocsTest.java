package com.faithlog.weeklymaterial.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.security.AccessTokenBlacklistChecker;
import com.faithlog.global.security.AccessTokenVersionChecker;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.SessionRevocationChecker;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.WeeklyMaterialCommandService;
import com.faithlog.weeklymaterial.service.WeeklyMaterialQueryService;
import com.faithlog.weeklymaterial.service.WeeklyMaterialAdminService;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialFileResult;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialWeekResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminWeeklyMaterialController.class, WeeklyMaterialController.class})
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class WeeklyMaterialApiRestDocsTest {
	@Autowired MockMvc mockMvc;
	@MockitoBean WeeklyMaterialCommandService commands;
	@MockitoBean WeeklyMaterialQueryService queries;
	@MockitoBean WeeklyMaterialAdminService admin;
	@MockitoBean JwtProvider jwtProvider;
	@MockitoBean AccessTokenBlacklistChecker accessTokenBlacklistChecker;
	@MockitoBean AccessTokenVersionChecker accessTokenVersionChecker;
	@MockitoBean SessionRevocationChecker sessionRevocationChecker;
	private final LocalDate week = LocalDate.of(2026, 8, 3);

	@BeforeEach void authenticate() {
		var principal = new AuthenticatedUser(11L, "MANAGER", "session", "jti", Instant.MAX);
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}
	@AfterEach void clear() { SecurityContextHolder.clearContext(); }

	@Test void documentsPutDeleteCurrentWeekAndList() throws Exception {
		when(queries.getWeek(7L, 11L, week)).thenReturn(weekResult());
		when(admin.putAndGet(7L, week, WeeklyMaterialType.SHARING_SHEET, 41L, 11L)).thenReturn(weekResult());
		when(queries.getCurrent(7L, 11L)).thenReturn(weekResult());
		when(queries.list(7L, 11L, 2026, 0, 20)).thenReturn(
			new PageImpl<>(List.of(weekResult()), PageRequest.of(0, 20), 1));

		mockMvc.perform(put("/api/v1/admin/campuses/{campusId}/weekly-materials/{weekStartDate}/{materialType}",
				7L, week, "SHARING_SHEET").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mediaAssetId\":41}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.sharingSheet.assetId").value(41))
			.andDo(document("weekly-material-put-success", preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()), slotPath(), requestFields(
					fieldWithPath("mediaAssetId").description("같은 캠퍼스·요청자 소유 READY PDF asset ID")),
				weekResponseFields()));

		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/weekly-materials/{weekStartDate}/{materialType}",
				7L, week, "SHARING_SHEET"))
			.andExpect(status().isNoContent()).andDo(document("weekly-material-delete-success", slotPath()));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/weekly-materials/current", 7L))
			.andExpect(status().isOk()).andDo(document("weekly-material-current-success",
				pathParameters(parameterWithName("campusId").description("ACTIVE 멤버 캠퍼스 ID")),
				weekResponseFields()));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/weekly-materials/{weekStartDate}", 7L, week))
			.andExpect(status().isOk()).andDo(document("weekly-material-week-success",
				pathParameters(parameterWithName("campusId").description("ACTIVE 멤버 캠퍼스 ID"),
					parameterWithName("weekStartDate").description("Asia/Seoul 기준 월요일 yyyy-MM-dd")),
				weekResponseFields()));

		mockMvc.perform(get("/api/v1/campuses/{campusId}/weekly-materials", 7L)
				.queryParam("year", "2026").queryParam("page", "0").queryParam("size", "20"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].weekStartDate").value("2026-08-03"))
			.andDo(document("weekly-material-list-success",
				preprocessResponse(prettyPrint()),
				pathParameters(parameterWithName("campusId").description("ACTIVE 멤버 캠퍼스 ID")),
				queryParameters(parameterWithName("year").description("조회 연도"),
					parameterWithName("page").description("0부터 시작하는 페이지"),
					parameterWithName("size").description("1~100 페이지 크기"))));
	}

	@Test void documentsValidationForbiddenNotFoundAndConflict() throws Exception {
		mockMvc.perform(put("/api/v1/admin/campuses/{campusId}/weekly-materials/{weekStartDate}/{materialType}",
				7L, week, "SHARING_SHEET").contentType(MediaType.APPLICATION_JSON).content("{\"mediaAssetId\":0}"))
			.andExpect(status().isBadRequest()).andDo(document("weekly-material-validation"));
		doThrow(new BusinessException(ErrorCode.WEEKLY_MATERIAL_MANAGE_FORBIDDEN))
			.when(commands).delete(7L, week, WeeklyMaterialType.SHARING_SHEET, 11L);
		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/weekly-materials/{weekStartDate}/{materialType}",
				7L, week, "SHARING_SHEET")).andExpect(status().isForbidden())
			.andDo(document("weekly-material-manage-forbidden"));
		when(queries.getWeek(7L, 11L, week)).thenReturn(new WeeklyMaterialWeekResult(week, null, null));
		when(queries.getCurrent(7L, 11L)).thenReturn(new WeeklyMaterialWeekResult(week, null, null));
		mockMvc.perform(get("/api/v1/campuses/{campusId}/weekly-materials/{weekStartDate}", 7L, week))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.shepherdGuide").value(nullValue()))
			.andExpect(jsonPath("$.data.sharingSheet").value(nullValue()))
			.andDo(document("weekly-material-week-empty-success", relaxedResponseFields(
				fieldWithPath("data.weekStartDate").description("조회한 주차 월요일"),
				fieldWithPath("data.shepherdGuide").type(org.springframework.restdocs.payload.JsonFieldType.NULL)
					.description("자료가 없으면 null"),
				fieldWithPath("data.sharingSheet").type(org.springframework.restdocs.payload.JsonFieldType.NULL)
					.description("자료가 없으면 null"))));
		mockMvc.perform(get("/api/v1/campuses/{campusId}/weekly-materials/current", 7L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.shepherdGuide").value(nullValue()))
			.andExpect(jsonPath("$.data.sharingSheet").value(nullValue()))
			.andDo(document("weekly-material-current-empty-success"));
		doThrow(new BusinessException(ErrorCode.MEDIA_ASSET_STATE_CONFLICT)).when(admin)
			.putAndGet(7L, week, WeeklyMaterialType.SHARING_SHEET, 41L, 11L);
		mockMvc.perform(put("/api/v1/admin/campuses/{campusId}/weekly-materials/{weekStartDate}/{materialType}",
				7L, week, "SHARING_SHEET").contentType(MediaType.APPLICATION_JSON)
			.content("{\"mediaAssetId\":41}"))
			.andExpect(status().isConflict()).andDo(document("weekly-material-media-conflict"));
	}

	private WeeklyMaterialWeekResult weekResult() {
		return new WeeklyMaterialWeekResult(week, null, new WeeklyMaterialFileResult(41L,
			WeeklyMaterialType.SHARING_SHEET, "주일설교-나눔지.pdf", 2048L, "a".repeat(64),
			Instant.parse("2026-08-03T04:00:00Z")));
	}
	private static org.springframework.restdocs.snippet.Snippet slotPath() {
		return pathParameters(parameterWithName("campusId").description("캠퍼스 ID"),
			parameterWithName("weekStartDate").description("Asia/Seoul 기준 월요일 yyyy-MM-dd"),
			parameterWithName("materialType").description("SHEPHERD_GUIDE 또는 SHARING_SHEET"));
	}
	private static org.springframework.restdocs.snippet.Snippet weekResponseFields() {
		return relaxedResponseFields(fieldWithPath("data.weekStartDate").description("주차 월요일"),
			fieldWithPath("data.shepherdGuide").type(org.springframework.restdocs.payload.JsonFieldType.OBJECT)
				.optional().description("nullable 목자지침 PDF"),
			fieldWithPath("data.sharingSheet").type(org.springframework.restdocs.payload.JsonFieldType.OBJECT)
				.optional().description("nullable 주일설교 나눔지 PDF"),
			fieldWithPath("data.sharingSheet.assetId").optional().description("private media access API용 asset ID"),
			fieldWithPath("data.sharingSheet.sha256").optional().description("assetId와 함께 사용하는 cache key"));
	}
}
