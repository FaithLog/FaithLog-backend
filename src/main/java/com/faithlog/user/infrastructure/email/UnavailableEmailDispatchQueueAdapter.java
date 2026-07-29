package com.faithlog.user.infrastructure.email;

import com.faithlog.user.service.command.QueueVerificationEmailCommand;
import com.faithlog.user.service.port.EmailDispatchQueueException;
import com.faithlog.user.service.port.EmailDispatchQueuePort;

public class UnavailableEmailDispatchQueueAdapter implements EmailDispatchQueuePort {

	@Override
	public void enqueue(QueueVerificationEmailCommand command) {
		throw new EmailDispatchQueueException("Email dispatch queue is not configured");
	}
}
