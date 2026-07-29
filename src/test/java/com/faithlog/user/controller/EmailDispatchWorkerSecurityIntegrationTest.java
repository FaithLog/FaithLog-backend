package com.faithlog.user.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.faithlog.user.service.EmailDispatchWorkerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@SpringBootTest(properties = {
	"faithlog.auth.email-dispatch.worker-enabled=true",
	"faithlog.auth.email-dispatch.oidc-audience=https://worker.example.com",
	"faithlog.auth.email-dispatch.oidc-service-account-email=tasks@example.iam.gserviceaccount.com"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailDispatchWorkerSecurityIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private EmailDispatchWorkerService workerService;

	@Test
	void anonymous_request_cannot_reach_the_internal_worker() throws Exception {
		mockMvc.perform(post("/internal/v1/email-dispatch/tasks")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"dispatchToken\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticated_google_oidc_principal_reaches_the_worker_chain() throws Exception {
		String dispatchToken = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
		mockMvc.perform(post("/internal/v1/email-dispatch/tasks")
			.with(jwt().jwt(token -> token
				.issuer("https://accounts.google.com")
				.audience(java.util.List.of("https://worker.example.com"))
				.claim("email", "tasks@example.iam.gserviceaccount.com")
				.claim("email_verified", true)))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"dispatchToken\":\"" + dispatchToken + "\"}"))
			.andExpect(status().isNoContent());

		verify(workerService).dispatch(dispatchToken);
	}
}
