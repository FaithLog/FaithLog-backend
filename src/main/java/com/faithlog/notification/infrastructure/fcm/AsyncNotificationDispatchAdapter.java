package com.faithlog.notification.infrastructure.fcm;

import com.faithlog.notification.application.NotificationDeliveryWorker;
import com.faithlog.notification.application.port.NotificationDispatchPort;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class AsyncNotificationDispatchAdapter implements NotificationDispatchPort {

	private final NotificationDeliveryWorker deliveryWorker;
	private final TaskExecutor taskExecutor;

	public AsyncNotificationDispatchAdapter(
		NotificationDeliveryWorker deliveryWorker,
		@Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor
	) {
		this.deliveryWorker = deliveryWorker;
		this.taskExecutor = taskExecutor;
	}

	@Override
	public void dispatch(UUID requestId) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			taskExecutor.execute(() -> deliveryWorker.processRequest(requestId));
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				taskExecutor.execute(() -> deliveryWorker.processRequest(requestId));
			}
		});
	}
}
