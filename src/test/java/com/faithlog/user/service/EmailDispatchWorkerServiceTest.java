package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.user.service.port.EmailDeliveryException;
import com.faithlog.user.service.port.EmailDispatchStore;
import com.faithlog.user.service.port.EmailDispatchStore.EmailDispatchPayload;
import com.faithlog.user.service.port.EmailSenderPort;
import com.faithlog.user.service.port.OneTimeTokenGenerator;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailDispatchWorkerServiceTest {

	@Mock
	private EmailDispatchStore dispatchStore;

	@Mock
	private EmailSenderPort emailSender;

	@Mock
	private OneTimeTokenGenerator tokenGenerator;

	private EmailDispatchWorkerService service;

	@BeforeEach
	void setUp() {
		when(tokenGenerator.generate()).thenReturn("lease-token");
		service = new EmailDispatchWorkerService(dispatchStore, emailSender, tokenGenerator);
	}

	@Test
	void sends_real_delivery_in_the_worker_then_acknowledges() {
		EmailDispatchPayload payload = payload(true);
		when(dispatchStore.acquire("dispatch-token", "lease-token", Duration.ofMinutes(2)))
			.thenReturn(Optional.of(payload));
		when(dispatchStore.acknowledge("dispatch-token", "lease-token")).thenReturn(true);

		service.dispatch("dispatch-token");

		verify(emailSender).sendVerificationCode(
			EmailVerificationPurpose.PASSWORD_RESET,
			"private@example.com",
			"123456",
			Duration.ofMinutes(5)
		);
		verify(dispatchStore).acknowledge("dispatch-token", "lease-token");
	}

	@Test
	void missing_account_dummy_delivery_uses_the_same_worker_without_calling_the_provider() {
		when(dispatchStore.acquire("dispatch-token", "lease-token", Duration.ofMinutes(2)))
			.thenReturn(Optional.of(payload(false)));
		when(dispatchStore.acknowledge("dispatch-token", "lease-token")).thenReturn(true);

		service.dispatch("dispatch-token");

		verify(emailSender, never()).sendVerificationCode(any(), anyString(), anyString(), any());
		verify(dispatchStore).acknowledge("dispatch-token", "lease-token");
	}

	@Test
	void provider_failure_releases_the_lease_for_cloud_tasks_retry() {
		EmailDispatchPayload payload = payload(true);
		when(dispatchStore.acquire("dispatch-token", "lease-token", Duration.ofMinutes(2)))
			.thenReturn(Optional.of(payload));
		org.mockito.Mockito.doThrow(new EmailDeliveryException("provider unavailable"))
			.when(emailSender).sendVerificationCode(any(), anyString(), anyString(), any());

		assertThatThrownBy(() -> service.dispatch("dispatch-token"))
			.isInstanceOf(EmailDeliveryException.class);
		verify(dispatchStore).release("dispatch-token", "lease-token");
		verify(dispatchStore, never()).acknowledge(anyString(), anyString());
	}

	@Test
	void an_in_progress_task_is_rejected_so_cloud_tasks_retries_it() {
		when(dispatchStore.acquire("dispatch-token", "lease-token", Duration.ofMinutes(2)))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.dispatch("dispatch-token"))
			.isInstanceOf(com.faithlog.user.service.port.EmailDispatchQueueException.class);

		verify(emailSender, never()).sendVerificationCode(any(), anyString(), anyString(), any());
		verify(dispatchStore, never()).acknowledge(anyString(), anyString());
	}

	private EmailDispatchPayload payload(boolean deliveryRequired) {
		return new EmailDispatchPayload(
			EmailVerificationPurpose.PASSWORD_RESET,
			"private@example.com",
			"123456",
			300,
			deliveryRequired
		);
	}
}
