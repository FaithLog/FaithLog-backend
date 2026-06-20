package com.faithlog.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationRestDocsIndexTest {

	@Test
	void rest_docs_index_includes_notification_api_sections() throws Exception {
		String index = Files.readString(Path.of("src/docs/asciidoc/index.adoc"));

		assertThat(index).contains("== Notifications");
		assertThat(index).contains("include::{snippets}/notification-register-fcm-token/http-request.adoc[]");
		assertThat(index).contains("include::{snippets}/notification-deactivate-fcm-token/http-request.adoc[]");
		assertThat(index).contains("include::{snippets}/notification-send-admin-notification/http-request.adoc[]");
		assertThat(index).contains("include::{snippets}/notification-list-notification-logs/http-request.adoc[]");
	}
}
