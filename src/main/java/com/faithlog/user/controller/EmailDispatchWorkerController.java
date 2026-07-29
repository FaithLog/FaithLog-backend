package com.faithlog.user.controller;

import com.faithlog.user.controller.dto.request.EmailDispatchTaskRequest;
import com.faithlog.user.service.EmailDispatchWorkerService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/email-dispatch")
@ConditionalOnProperty(name = "faithlog.auth.email-dispatch.worker-enabled", havingValue = "true")
public class EmailDispatchWorkerController {

	private final EmailDispatchWorkerService workerService;

	public EmailDispatchWorkerController(EmailDispatchWorkerService workerService) {
		this.workerService = workerService;
	}

	@PostMapping("/tasks")
	public ResponseEntity<Void> dispatch(@Valid @RequestBody EmailDispatchTaskRequest request) {
		workerService.dispatch(request.dispatchToken());
		return ResponseEntity.noContent().build();
	}
}
