package com.faithlog.media.controller;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.service.MediaAssetCommandService;
import com.faithlog.media.service.MediaAssetQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MediaAssetController {
	private final MediaAssetCommandService commands;
	private final MediaAssetQueryService queries;
	public MediaAssetController(MediaAssetCommandService commands, MediaAssetQueryService queries) {
		this.commands = commands; this.queries = queries;
	}

	@PostMapping("/api/v1/admin/campuses/{campusId}/media-assets/upload-reservations")
	public ResponseEntity<ApiResponse<UploadReservationResponse>> reserve(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long campusId,
		@Valid @RequestBody UploadReservationRequest request) {
		var result = commands.reserve(campusId, user.userId(), request.contentType(), request.byteSize(),
			request.sha256(), request.fileName());
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(new UploadReservationResponse(
			result.assetId(), result.uploadUrl(), result.requiredHeaders(), result.expiresAt())));
	}

	@PostMapping("/api/v1/admin/campuses/{campusId}/media-assets/{assetId}/complete")
	public ApiResponse<com.faithlog.media.service.result.MediaAssetResult> complete(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long campusId, @PathVariable Long assetId) {
		return ApiResponse.success(commands.complete(campusId, assetId, user.userId()));
	}

	@PostMapping("/api/v1/campuses/{campusId}/media-assets/access-urls")
	public ApiResponse<List<com.faithlog.media.service.result.MediaAccessUrlResult>> accessUrls(
		@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long campusId,
		@Valid @RequestBody AccessUrlRequest request) {
		return ApiResponse.success(queries.getAccessUrls(campusId, user.userId(), request.assetIds()));
	}

	public record UploadReservationRequest(
		@NotBlank @Pattern(regexp = "^(image/(jpeg|png)|application/pdf)$") String contentType,
		@Positive @Max(MediaAsset.MAX_PDF_INPUT_BYTES) long byteSize,
		@NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String sha256,
		@Size(max = 255) String fileName) {
		public UploadReservationRequest(String contentType, long byteSize, String sha256) {
			this(contentType, byteSize, sha256, null);
		}

		@AssertTrue(message = "media byte size is invalid")
		public boolean isByteSize() {
			if ("application/pdf".equals(contentType)) {
				return byteSize <= MediaAsset.MAX_PDF_INPUT_BYTES;
			}
			return byteSize <= MediaAsset.MAX_INPUT_BYTES;
		}

		@AssertTrue(message = "PDF file name is required")
		public boolean isFileName() {
			return !"application/pdf".equals(contentType) || (fileName != null && !fileName.isBlank());
		}
	}
	public record UploadReservationResponse(Long assetId, URI uploadUrl, Map<String, String> requiredHeaders, Instant expiresAt) {}
	public record AccessUrlRequest(@NotEmpty @Size(max = 100) List<@Positive Long> assetIds) {}
}
