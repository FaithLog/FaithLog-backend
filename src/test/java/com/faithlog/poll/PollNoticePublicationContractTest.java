package com.faithlog.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.poll.controller.dto.response.PollDetailResponse;
import com.faithlog.poll.controller.dto.response.PollListResponse;
import com.faithlog.poll.controller.dto.response.PollResponse;
import com.faithlog.poll.controller.AdminPollController;
import com.faithlog.poll.controller.MealPollController;
import com.faithlog.poll.domain.entity.Poll;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;

class PollNoticePublicationContractTest {

	@Test
	void poll_and_api_contract_expose_notice_without_copying_body_into_lists() throws Exception {
		Field notice = Poll.class.getDeclaredField("notice");
		assertThat(notice.getType()).isEqualTo(String.class);

		assertThat(componentNames(PollResponse.class)).contains("notice");
		assertThat(componentNames(PollDetailResponse.class)).contains("notice");
		assertThat(componentNames(PollListResponse.class))
			.contains("hasNotice")
			.doesNotContain("notice");
	}

	@Test
	void poll_supports_atomic_title_and_notice_update() throws Exception {
		Method update = Poll.class.getDeclaredMethod("updateTitleAndNotice", String.class, String.class);
		assertThat(update.getReturnType()).isEqualTo(void.class);
	}

	@Test
	void all_poll_types_have_distinct_open_notification_types() {
		assertThatCode(() -> NotificationType.valueOf("MEAL_POLL_OPEN")).doesNotThrowAnyException();
		assertThatCode(() -> NotificationType.valueOf("CUSTOM_POLL_OPEN")).doesNotThrowAnyException();
		assertThat(NotificationType.valueOf("WED_POLL_OPEN")).isNotNull();
		assertThat(NotificationType.valueOf("SATURDAY_POLL_OPEN")).isNotNull();
		assertThat(NotificationType.valueOf("COFFEE_POLL_OPEN")).isNotNull();
	}

	@Test
	void durable_poll_publication_outbox_contract_exists() throws Exception {
		Class<?> outbox = Class.forName("com.faithlog.poll.domain.entity.PollNotificationOutbox");
		assertThat(outbox.getDeclaredField("pollId").getType()).isEqualTo(Long.class);
		assertThat(outbox.getDeclaredField("processedAt").getType()).isEqualTo(java.time.Instant.class);
	}

	@Test
	void title_and_notice_request_trims_blank_and_enforces_approved_limits() throws Exception {
		Class<?> requestType = Class.forName("com.faithlog.poll.controller.dto.request.UpdatePollNoticeRequest");
		var constructor = requestType.getDeclaredConstructor(String.class, String.class);
		Object normalized = constructor.newInstance("  수정 제목  ", "  공지 본문  ");
		assertThat(requestType.getMethod("title").invoke(normalized)).isEqualTo("수정 제목");
		assertThat(requestType.getMethod("notice").invoke(normalized)).isEqualTo("공지 본문");

		Object blankNotice = constructor.newInstance("제목", "   ");
		assertThat(requestType.getMethod("notice").invoke(blankNotice)).isNull();

		try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
			var validator = validatorFactory.getValidator();
			assertThat(validator.validate(constructor.newInstance("제목", "가".repeat(5_000)))).isEmpty();
			assertThat(validator.validate(constructor.newInstance("제목", "가".repeat(5_001)))).isNotEmpty();
			assertThat(validator.validate(constructor.newInstance("가".repeat(200), null))).isEmpty();
			assertThat(validator.validate(constructor.newInstance("가".repeat(201), null))).isNotEmpty();
		}
	}

	@Test
	void generic_and_meal_notice_patch_paths_are_separate() throws Exception {
		assertPatchPath(AdminPollController.class, "updatePollNotice", "/{pollId}/notice");
		assertPatchPath(MealPollController.class, "updatePollNotice", "/{pollId}/notice");
	}

	private Set<String> componentNames(Class<?> recordType) {
		return Arrays.stream(recordType.getRecordComponents())
			.map(RecordComponent::getName)
			.collect(Collectors.toSet());
	}

	private void assertPatchPath(Class<?> controllerType, String methodName, String expectedPath) {
		Method method = Arrays.stream(controllerType.getDeclaredMethods())
			.filter(candidate -> candidate.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
		assertThat(method.getAnnotation(PatchMapping.class).value()).containsExactly(expectedPath);
	}
}
