package com.faithlog.notification.service;

import com.faithlog.notification.service.command.SendNotificationCommand;
import com.faithlog.notification.service.result.SendNotificationResult;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

	private final NotificationRequestCommandService commandService;

	public NotificationService(NotificationRequestCommandService commandService) {
		this.commandService = commandService;
	}

	public SendNotificationResult requestNotification(SendNotificationCommand command) {
		return commandService.requestNotification(command);
	}

}
