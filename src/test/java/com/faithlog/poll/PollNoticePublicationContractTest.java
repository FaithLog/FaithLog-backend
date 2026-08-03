package com.faithlog.poll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.poll.controller.dto.response.PollDetailResponse;
import com.faithlog.poll.controller.dto.response.PollListResponse;
import com.faithlog.poll.controller.dto.response.PollResponse;
import com.faithlog.poll.domain.entity.Poll;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

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

	private Set<String> componentNames(Class<?> recordType) {
		return Arrays.stream(recordType.getRecordComponents())
			.map(RecordComponent::getName)
			.collect(Collectors.toSet());
	}
}
