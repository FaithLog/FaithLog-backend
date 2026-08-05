package com.faithlog.announcement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faithlog.announcement.domain.type.AnnouncementStatus;
import com.faithlog.announcement.service.AnnouncementCategoryCommandService;
import com.faithlog.announcement.service.AnnouncementCategoryQueryService;
import com.faithlog.announcement.service.AnnouncementCommandService;
import com.faithlog.announcement.service.AnnouncementQueryService;
import com.faithlog.announcement.service.result.AnnouncementCategoryResult;
import com.faithlog.announcement.service.result.AnnouncementResult;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.security.AccessTokenBlacklistChecker;
import com.faithlog.global.security.AccessTokenVersionChecker;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.global.security.JwtProvider;
import com.faithlog.global.security.SessionRevocationChecker;
import com.faithlog.media.controller.MediaAssetController;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.domain.type.MediaAssetKind;
import com.faithlog.media.service.MediaAssetCommandService;
import com.faithlog.media.service.MediaAssetQueryService;
import com.faithlog.media.service.result.MediaAccessUrlResult;
import com.faithlog.media.service.result.MediaAssetResult;
import com.faithlog.media.service.result.MediaUploadReservationResult;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AdminAnnouncementController.class, AnnouncementController.class, MediaAssetController.class})
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureRestDocs(outputDir = "build/generated-snippets")
class AnnouncementApiRestDocsTest {

	private static final Instant NOW = Instant.parse("2026-08-03T04:00:00Z");
	@Autowired private MockMvc mockMvc;
	@MockitoBean private AnnouncementCategoryCommandService categoryCommands;
	@MockitoBean private AnnouncementCategoryQueryService categoryQueries;
	@MockitoBean private AnnouncementCommandService announcementCommands;
	@MockitoBean private AnnouncementQueryService announcementQueries;
	@MockitoBean private MediaAssetCommandService mediaCommands;
	@MockitoBean private MediaAssetQueryService mediaQueries;
	@MockitoBean private JwtProvider jwtProvider;
	@MockitoBean private AccessTokenBlacklistChecker accessTokenBlacklistChecker;
	@MockitoBean private AccessTokenVersionChecker accessTokenVersionChecker;
	@MockitoBean private SessionRevocationChecker sessionRevocationChecker;

	@BeforeEach
	void authenticate() {
		var principal = new AuthenticatedUser(11L, "MANAGER", "session", "jti", NOW.plusSeconds(3600));
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void documents_announcement_create_with_ordered_images_and_pdfs() throws Exception {
		when(announcementCommands.createAnnouncement(any())).thenReturn(announcementResult());

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/announcements", 7L)
				.header("Authorization", "Bearer access-token")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"categoryId":3,"title":"수련회 안내","content":"준비물을 확인해 주세요.",
					 "isPinned":true,"publishAt":null,"imageAssetIds":[31,32],"documentAssetIds":[41]}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.data.imageAssetIds[0]").value(31))
			.andExpect(jsonPath("$.data.documentAssetIds[0]").value(41))
			.andDo(document("announcement-create-success",
				preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()), authHeader(), campusPath(),
				requestFields(
					fieldWithPath("categoryId").description("ACTIVE 공지 카테고리 ID"),
					fieldWithPath("title").description("trim 후 1~100자 제목"),
					fieldWithPath("content").description("trim 후 1~5,000자 일반 텍스트"),
					fieldWithPath("isPinned").description("상단 고정 여부"),
					fieldWithPath("publishAt").optional().description("null이면 즉시 게시, 미래 시각이면 예약 게시"),
					fieldWithPath("imageAssetIds").description("READY 이미지 ID의 표시 순서. 빈 배열 가능"),
					fieldWithPath("imageAssetIds[]").description("동일 캠퍼스·요청자 소유 READY 이미지 asset ID"),
					fieldWithPath("documentAssetIds").description("READY PDF ID의 표시 순서. 빈 배열 가능"),
					fieldWithPath("documentAssetIds[]").description("동일 캠퍼스·요청자 소유 READY PDF asset ID")
				),
				relaxedResponseFields(
					fieldWithPath("data.id").description("공지 ID"),
					fieldWithPath("data.status").description("PUBLISHED 또는 SCHEDULED"),
					fieldWithPath("data.imageAssetIds").description("저장된 이미지 순서"),
					fieldWithPath("data.imageAssetIds[]").description("배치 access URL 요청에 사용할 이미지 asset ID"),
					fieldWithPath("data.documentAssetIds").description("저장된 PDF 순서"),
					fieldWithPath("data.documentAssetIds[]").description("배치 access URL 요청에 사용할 PDF asset ID")
				)));
	}

	@Test
	void documents_announcement_validation_authorization_not_found_and_conflict() throws Exception {
		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/announcements", 7L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":3,\"title\":\" \",\"content\":\"본문\",\"isPinned\":false,\"imageAssetIds\":[]}"))
			.andExpect(status().isBadRequest())
			.andDo(document("announcement-create-validation", preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()), campusPath()));

		doThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN))
			.when(categoryCommands).deactivateCategory(7L, 3L, 11L);
		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/announcement-categories/{categoryId}/deactivate", 7L, 3L))
			.andExpect(status().isForbidden())
			.andDo(document("announcement-manage-forbidden", preprocessResponse(prettyPrint()),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("categoryId").description("카테고리 ID"))));

		when(announcementQueries.getAnnouncement(7L, 99L, 11L))
			.thenThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/campuses/{campusId}/announcements/{announcementId}", 7L, 99L))
			.andExpect(status().isNotFound())
			.andDo(document("announcement-not-found", preprocessResponse(prettyPrint()),
				pathParameters(parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("announcementId").description("공지 ID"))));

		when(announcementCommands.updateAnnouncement(any()))
			.thenThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT));
		mockMvc.perform(patch("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}", 7L, 99L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryId\":3,\"title\":\"수정\",\"content\":\"본문\",\"isPinned\":false,\"imageAssetIds\":[]}"))
			.andExpect(status().isConflict())
			.andDo(document("announcement-status-conflict", preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()), pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("announcementId").description("공지 ID"))));
	}

	@Test
	void documents_announcement_delete_success_not_found_conflict_and_forbidden() throws Exception {
		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}", 7L, 99L)
				.header("Authorization", "Bearer access-token"))
			.andExpect(status().isNoContent())
			.andDo(document("announcement-delete-success",
				authHeader(),
				pathParameters(
					parameterWithName("campusId").description("삭제할 ARCHIVED 공지의 캠퍼스 ID"),
					parameterWithName("announcementId").description("ARCHIVED 상태 공지 ID"))));

		doThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_NOT_FOUND))
			.when(announcementCommands).deleteAnnouncement(7L, 404L, 11L);
		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}", 7L, 404L))
			.andExpect(status().isNotFound())
			.andDo(document("announcement-delete-not-found", preprocessResponse(prettyPrint()),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("announcementId").description("없거나 이미 삭제된 공지 ID"))));

		doThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT))
			.when(announcementCommands).deleteAnnouncement(7L, 409L, 11L);
		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}", 7L, 409L))
			.andExpect(status().isConflict())
			.andDo(document("announcement-delete-status-conflict", preprocessResponse(prettyPrint()),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("announcementId").description("SCHEDULED 또는 PUBLISHED 상태 공지 ID"))));

		doThrow(new BusinessException(ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN))
			.when(announcementCommands).deleteAnnouncement(7L, 403L, 11L);
		mockMvc.perform(delete("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}", 7L, 403L))
			.andExpect(status().isForbidden())
			.andDo(document("announcement-delete-forbidden", preprocessResponse(prettyPrint()),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("announcementId").description("관리 권한이 필요한 공지 ID"))));
	}

	@Test
	void documents_announcement_restore_success_not_found_conflict_and_forbidden() throws Exception {
		stubRestore(announcementResult(), 7L, 99L, 11L);
		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}/restore", 7L, 99L)
				.header("Authorization", "Bearer access-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.data.publishedAt").value("2026-08-03T04:00:00Z"))
			.andExpect(jsonPath("$.data.imageAssetIds[0]").value(31))
			.andExpect(jsonPath("$.data.documentAssetIds[0]").value(41))
			.andDo(document("announcement-restore-success",
				preprocessResponse(prettyPrint()), authHeader(),
				pathParameters(
					parameterWithName("campusId").description("복구할 ARCHIVED 공지의 캠퍼스 ID"),
					parameterWithName("announcementId").description("ARCHIVED 상태 공지 ID")),
				relaxedResponseFields(
					fieldWithPath("data.id").description("복구된 공지 ID"),
					fieldWithPath("data.status").description("복구 후 PUBLISHED"),
					fieldWithPath("data.publishedAt").description("원래 게시 시각 또는 복구 시점 서버 Clock"),
					fieldWithPath("data.imageAssetIds").description("복구 후 보존된 이미지 순서"),
					fieldWithPath("data.documentAssetIds").description("복구 후 보존된 PDF 순서"))));

		stubRestoreFailure(ErrorCode.ANNOUNCEMENT_NOT_FOUND, 7L, 404L, 11L);
		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}/restore", 7L, 404L))
			.andExpect(status().isNotFound())
			.andDo(document("announcement-restore-not-found", preprocessResponse(prettyPrint()),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("announcementId").description("없거나 삭제됐거나 다른 캠퍼스의 공지 ID"))));

		stubRestoreFailure(ErrorCode.ANNOUNCEMENT_STATUS_CONFLICT, 7L, 409L, 11L);
		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}/restore", 7L, 409L))
			.andExpect(status().isConflict())
			.andDo(document("announcement-restore-status-conflict", preprocessResponse(prettyPrint()),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("announcementId").description("SCHEDULED 또는 PUBLISHED 상태 공지 ID"))));

		stubRestoreFailure(ErrorCode.ANNOUNCEMENT_MANAGE_FORBIDDEN, 7L, 403L, 11L);
		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/announcements/{announcementId}/restore", 7L, 403L)
				.header("Authorization", "Bearer access-token"))
			.andExpect(status().isForbidden())
			.andDo(document("announcement-restore-forbidden", preprocessResponse(prettyPrint()),
				pathParameters(
					parameterWithName("campusId").description("캠퍼스 ID"),
					parameterWithName("announcementId").description("관리 권한이 필요한 공지 ID"))));
	}

	@Test
	void documents_media_upload_reservation_complete_and_batched_access_urls() throws Exception {
		when(mediaCommands.reserve(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(),
			nullable(String.class)))
			.thenReturn(new MediaUploadReservationResult(31L, URI.create("https://upload.example/asset"),
				Map.of("Content-Type", "image/jpeg"), NOW.plusSeconds(600)));
		when(mediaCommands.complete(7L, 31L, 11L)).thenReturn(
			new MediaAssetResult(31L, 7L, MediaAssetStatus.READY, "b".repeat(64), 1600, 1200, 12345L));
		when(mediaQueries.getAccessUrls(7L, 11L, List.of(31L, 32L))).thenReturn(List.of(
			new MediaAccessUrlResult(31L, "b".repeat(64), URI.create("https://download.example/31-thumb"),
				URI.create("https://download.example/31-detail"), NOW.plusSeconds(600)),
			new MediaAccessUrlResult(32L, MediaAssetKind.PDF, "application/pdf", "weekly-guide.pdf", 2048L,
				"c".repeat(64), null, null, URI.create("https://download.example/32-document"),
				NOW.plusSeconds(600))));

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/media-assets/upload-reservations", 7L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"contentType\":\"image/jpeg\",\"byteSize\":12345,\"sha256\":\"%s\"}"
					.formatted("a".repeat(64))))
			.andExpect(status().isCreated())
			.andDo(document("media-upload-reservation", preprocessRequest(prettyPrint()),
				preprocessResponse(prettyPrint()), campusPath(), requestFields(
					fieldWithPath("contentType").description("image/jpeg, image/png 또는 application/pdf"),
					fieldWithPath("byteSize").description("이미지 5MiB, PDF 30MiB 이하 입력 크기"),
					fieldWithPath("sha256").description("입력 파일의 lowercase SHA-256"),
					fieldWithPath("fileName").optional().type(JsonFieldType.STRING)
						.description("PDF일 때 필수인 표시 파일명")),
				relaxedResponseFields(
					fieldWithPath("data.assetId").description("예약된 media asset ID"),
					fieldWithPath("data.uploadUrl").description("짧은 수명의 private R2 Presigned PUT URL"),
					fieldWithPath("data.requiredHeaders").description("PUT에 그대로 포함할 서명 header"),
					fieldWithPath("data.expiresAt").description("업로드 URL 만료 시각"))));

		mockMvc.perform(post("/api/v1/admin/campuses/{campusId}/media-assets/{assetId}/complete", 7L, 31L))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("READY"))
			.andDo(document("media-upload-complete", preprocessResponse(prettyPrint()), pathParameters(
				parameterWithName("campusId").description("캠퍼스 ID"),
				parameterWithName("assetId").description("PENDING asset ID"))));

		mockMvc.perform(post("/api/v1/campuses/{campusId}/media-assets/access-urls", 7L)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"assetIds\":[31,32]}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].assetId").value(31))
			.andExpect(jsonPath("$.data[0].sha256").value("b".repeat(64)))
			.andDo(document("media-access-urls", preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()),
				campusPath(), requestFields(
					fieldWithPath("assetIds").description("원래 순서를 보존하는 asset ID 배열. 요청당 최대 100개"),
					fieldWithPath("assetIds[]").description("접근 권한을 확인할 asset ID")),
				relaxedResponseFields(
					fieldWithPath("data[]").description("입력 순서와 동일한 signed URL 결과"),
					fieldWithPath("data[].assetId").description("asset ID"),
					fieldWithPath("data[].assetKind").description("IMAGE 또는 PDF"),
					fieldWithPath("data[].contentType").description("검증된 응답 content type"),
					fieldWithPath("data[].fileName").optional().description("PDF 표시 파일명"),
					fieldWithPath("data[].byteSize").optional().description("검증 후 저장된 파일 크기"),
					fieldWithPath("data[].sha256").description("기기 variant cache key에 사용하는 immutable SHA-256"),
					fieldWithPath("data[].thumbnailUrl").optional().description("이미지용 10분 수명 thumbnail URL"),
					fieldWithPath("data[].detailUrl").optional().description("이미지용 10분 수명 detail URL"),
					fieldWithPath("data[].downloadUrl").optional().description("PDF용 10분 수명 attachment URL"),
					fieldWithPath("data[].expiresAt").description("signed GET 만료 시각"))));
	}

	private AnnouncementResult announcementResult() {
		var category = new AnnouncementCategoryResult(3L, 7L, "일반", "#3B82F6", 0, true, NOW, NOW);
		return new AnnouncementResult(99L, 7L, category, 11L, "수련회 안내", "준비물을 확인해 주세요.", true,
			AnnouncementStatus.PUBLISHED, NOW, NOW, NOW, NOW, List.of(31L, 32L), List.of(41L));
	}

	private void stubRestore(AnnouncementResult result, Long campusId, Long announcementId, Long requesterId)
		throws Exception {
		restoreMethod().invoke(doReturn(result).when(announcementCommands), campusId, announcementId, requesterId);
	}

	private void stubRestoreFailure(ErrorCode errorCode, Long campusId, Long announcementId, Long requesterId)
		throws Exception {
		restoreMethod().invoke(doThrow(new BusinessException(errorCode)).when(announcementCommands),
			campusId, announcementId, requesterId);
	}

	private Method restoreMethod() throws NoSuchMethodException {
		return AnnouncementCommandService.class
			.getMethod("restoreAnnouncement", Long.class, Long.class, Long.class);
	}

	private static org.springframework.restdocs.snippet.Snippet authHeader() {
		return requestHeaders(headerWithName("Authorization").description("Bearer Access Token"));
	}

	private static org.springframework.restdocs.snippet.Snippet campusPath() {
		return pathParameters(parameterWithName("campusId").description("공지·이미지가 속한 캠퍼스 ID"));
	}
}
