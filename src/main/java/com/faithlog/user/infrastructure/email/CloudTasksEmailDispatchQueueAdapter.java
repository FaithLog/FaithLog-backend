package com.faithlog.user.infrastructure.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.service.command.QueueVerificationEmailCommand;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchQueuePort;
import com.faithlog.user.service.port.EmailDispatchStore;
import com.faithlog.user.service.port.EmailDispatchStore.EmailDispatchPayload;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.OidcToken;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "faithlog.auth.email-dispatch.cloud-tasks-enabled", havingValue = "true")
public class CloudTasksEmailDispatchQueueAdapter implements EmailDispatchQueuePort {

	private final CloudTasksClient cloudTasksClient;
	private final EmailDispatchStore dispatchStore;
	private final ObjectMapper objectMapper;
	private final String queuePath;
	private final String workerUrl;
	private final String serviceAccountEmail;
	private final String oidcAudience;

	public CloudTasksEmailDispatchQueueAdapter(
		CloudTasksClient cloudTasksClient,
		EmailDispatchStore dispatchStore,
		ObjectMapper objectMapper,
		@Value("${faithlog.auth.email-dispatch.project-id:}") String projectId,
		@Value("${faithlog.auth.email-dispatch.location:}") String location,
		@Value("${faithlog.auth.email-dispatch.queue-id:}") String queueId,
		@Value("${faithlog.auth.email-dispatch.worker-url:}") String workerUrl,
		@Value("${faithlog.auth.email-dispatch.oidc-service-account-email:}") String serviceAccountEmail,
		@Value("${faithlog.auth.email-dispatch.oidc-audience:}") String oidcAudience
	) {
		this.cloudTasksClient = cloudTasksClient;
		this.dispatchStore = dispatchStore;
		this.objectMapper = objectMapper;
		this.queuePath = QueueName.of(
			requireText(projectId, "project-id"),
			requireText(location, "location"),
			requireText(queueId, "queue-id")
		).toString();
		this.workerUrl = requireHttpsUrl(workerUrl);
		this.serviceAccountEmail = requireText(serviceAccountEmail, "oidc-service-account-email");
		this.oidcAudience = requireHttpsUrl(oidcAudience);
	}

	@Override
	public void enqueue(QueueVerificationEmailCommand command) {
		EmailDispatchPayload payload = new EmailDispatchPayload(
			command.purpose(),
			command.recipientEmail(),
			command.verificationCode(),
			command.ttl().toSeconds(),
			command.deliveryRequired()
		);
		String dispatchToken = dispatchStore.create(payload, command.ttl());
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.setHttpMethod(HttpMethod.POST)
				.setUrl(workerUrl)
				.putHeaders("Content-Type", "application/json")
				.setOidcToken(OidcToken.newBuilder()
					.setServiceAccountEmail(serviceAccountEmail)
					.setAudience(oidcAudience)
					.build())
				.setBody(ByteString.copyFrom(taskBody(dispatchToken), StandardCharsets.UTF_8))
				.build();
			cloudTasksClient.createTask(queuePath, Task.newBuilder().setHttpRequest(request).build());
		} catch (RuntimeException exception) {
			dispatchStore.discard(dispatchToken);
			throw new EmailDispatchQueueException("Email dispatch queue is unavailable", exception);
		}
	}

	private String taskBody(String dispatchToken) {
		try {
			return objectMapper.writeValueAsString(Map.of("dispatchToken", dispatchToken));
		} catch (JsonProcessingException exception) {
			throw new EmailDispatchQueueException("Email dispatch task cannot be created", exception);
		}
	}

	private String requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Email dispatch " + name + " must be configured");
		}
		return value;
	}

	private String requireHttpsUrl(String value) {
		String configured = requireText(value, "HTTPS URL");
		URI uri;
		try {
			uri = URI.create(configured);
		} catch (IllegalArgumentException exception) {
			throw new IllegalStateException("Email dispatch HTTPS URL is invalid", exception);
		}
		if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
			throw new IllegalStateException("Email dispatch HTTPS URL is invalid");
		}
		return configured;
	}
}
