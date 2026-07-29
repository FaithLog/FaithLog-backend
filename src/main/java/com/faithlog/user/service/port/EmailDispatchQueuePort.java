package com.faithlog.user.service.port;

import com.faithlog.user.service.command.QueueVerificationEmailCommand;

public interface EmailDispatchQueuePort {

	void enqueue(QueueVerificationEmailCommand command);
}
