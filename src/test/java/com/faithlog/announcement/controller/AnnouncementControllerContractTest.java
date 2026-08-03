package com.faithlog.announcement.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.announcement.controller.dto.request.CreateAnnouncementCategoryRequest;
import com.faithlog.announcement.controller.dto.request.CreateAnnouncementRequest;
import com.faithlog.announcement.controller.dto.request.UpdateAnnouncementCategoryRequest;
import com.faithlog.announcement.controller.dto.request.UpdateAnnouncementRequest;
import com.faithlog.announcement.controller.dto.response.AnnouncementResponse;
import jakarta.validation.Validation;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class AnnouncementControllerContractTest {

	@Test
	void exposes_the_approved_public_and_admin_routes() {
		assertThat(AnnouncementController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/api/v1/campuses/{campusId}");
		assertRoute(AnnouncementController.class, "getCategories", GetMapping.class, "/announcement-categories");
		assertRoute(AnnouncementController.class, "getAnnouncements", GetMapping.class, "/announcements");
		assertRoute(AnnouncementController.class, "getAnnouncement", GetMapping.class, "/announcements/{announcementId}");

		assertThat(AdminAnnouncementController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/api/v1/admin/campuses/{campusId}");
		assertRoute(AdminAnnouncementController.class, "createCategory", PostMapping.class, "/announcement-categories");
		assertRoute(AdminAnnouncementController.class, "updateCategory", PatchMapping.class,
			"/announcement-categories/{categoryId}");
		assertRoute(AdminAnnouncementController.class, "deactivateCategory", PostMapping.class,
			"/announcement-categories/{categoryId}/deactivate");
		assertRoute(AdminAnnouncementController.class, "createAnnouncement", PostMapping.class, "/announcements");
		assertRoute(AdminAnnouncementController.class, "updateAnnouncement", PatchMapping.class,
			"/announcements/{announcementId}");
		assertRoute(AdminAnnouncementController.class, "publishAnnouncement", PostMapping.class,
			"/announcements/{announcementId}/publish");
		assertRoute(AdminAnnouncementController.class, "archiveAnnouncement", PostMapping.class,
			"/announcements/{announcementId}/archive");
	}

	@Test
	void request_dtos_enforce_exact_text_color_and_order_boundaries() {
		var validator = Validation.buildDefaultValidatorFactory().getValidator();
		assertThat(validator.validate(new CreateAnnouncementCategoryRequest(" ", "#3B82F6", 0))).isNotEmpty();
		assertThat(validator.validate(new CreateAnnouncementCategoryRequest("a".repeat(31), "#3B82F6", 0))).isNotEmpty();
		assertThat(validator.validate(new UpdateAnnouncementCategoryRequest("일반", "blue", 0))).isNotEmpty();
		assertThat(validator.validate(new UpdateAnnouncementCategoryRequest("일반", "#3B82F6", -1))).isNotEmpty();
		assertThat(validator.validate(new CreateAnnouncementRequest(
			1L, "a".repeat(101), "본문", false, null))).isNotEmpty();
		assertThat(validator.validate(new UpdateAnnouncementRequest(
			1L, "제목", "a".repeat(5001), false, Instant.now().plusSeconds(60)))).isNotEmpty();
		assertThat(validator.validate(new CreateAnnouncementRequest(
			1L, "a".repeat(100), "a".repeat(5000), true, null))).isEmpty();
	}

	@Test
	void announcement_response_exposes_ordered_image_asset_ids_for_batched_access_urls() {
		assertThat(Arrays.stream(AnnouncementResponse.class.getRecordComponents())
			.map(component -> component.getName()))
			.contains("imageAssetIds");
	}

	private void assertRoute(
		Class<?> controller,
		String methodName,
		Class<? extends java.lang.annotation.Annotation> annotationType,
		String path
	) {
		Method method = Arrays.stream(controller.getDeclaredMethods())
			.filter(candidate -> candidate.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
		String[] values;
		if (annotationType == GetMapping.class) {
			values = method.getAnnotation(GetMapping.class).value();
		} else if (annotationType == PostMapping.class) {
			values = method.getAnnotation(PostMapping.class).value();
		} else {
			values = method.getAnnotation(PatchMapping.class).value();
		}
		assertThat(values).containsExactly(path);
	}
}
