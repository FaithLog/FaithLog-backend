package com.faithlog.user.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.service.EmailVerificationPurpose;
import com.faithlog.user.service.command.QueueVerificationEmailCommand;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchStore;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.Task;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CloudTasksEmailDispatchQueueAdapterTest {

	@Test
	void cloud_task_contains_only_the_opaque_dispatch_token_and_oidc_binding() {
		CloudTasksClient client = mock(CloudTasksClient.class);
		EmailDispatchStore store = mock(EmailDispatchStore.class);
		when(store.create(any(), any())).thenReturn("opaque-dispatch-token");
		CloudTasksEmailDispatchQueueAdapter adapter = adapter(client, store);

		adapter.enqueue(command());

		ArgumentCaptor<Task> task = ArgumentCaptor.forClass(Task.class);
		verify(client).createTask(
			org.mockito.ArgumentMatchers.eq("projects/project/locations/asia-northeast3/queues/email"),
			task.capture()
		);
		String body = task.getValue().getHttpRequest().getBody().toString(StandardCharsets.UTF_8);
		assertThat(body)
			.contains("opaque-dispatch-token")
			.doesNotContain("private@example.com", "123456");
		assertThat(task.getValue().getHttpRequest().getUrl())
			.isEqualTo("https://worker.example.com/internal/v1/email-dispatch/tasks");
		assertThat(task.getValue().getHttpRequest().getOidcToken().getServiceAccountEmail())
			.isEqualTo("tasks@example.iam.gserviceaccount.com");
		assertThat(task.getValue().getHttpRequest().getOidcToken().getAudience())
			.isEqualTo("https://worker.example.com");
	}

	@Test
	void task_creation_failure_discards_the_encrypted_payload() {
		CloudTasksClient client = mock(CloudTasksClient.class);
		EmailDispatchStore store = mock(EmailDispatchStore.class);
		when(store.create(any(), any())).thenReturn("opaque-dispatch-token");
		when(client.createTask(any(String.class), any(Task.class)))
			.thenThrow(new IllegalStateException("unavailable"));
		CloudTasksEmailDispatchQueueAdapter adapter = adapter(client, store);

		assertThatThrownBy(() -> adapter.enqueue(command()))
			.isInstanceOf(EmailDispatchQueueException.class);
		verify(store).discard("opaque-dispatch-token");
	}

	private CloudTasksEmailDispatchQueueAdapter adapter(CloudTasksClient client, EmailDispatchStore store) {
		return new CloudTasksEmailDispatchQueueAdapter(
			client,
			store,
			new ObjectMapper(),
			"project",
			"asia-northeast3",
			"email",
			"https://worker.example.com/internal/v1/email-dispatch/tasks",
			"tasks@example.iam.gserviceaccount.com",
			"https://worker.example.com"
		);
	}

	private QueueVerificationEmailCommand command() {
		return new QueueVerificationEmailCommand(
			EmailVerificationPurpose.PASSWORD_RESET,
			"private@example.com",
			"123456",
			Duration.ofMinutes(5),
			true
		);
	}
}
