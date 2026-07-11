package com.faithlog.notification.service;

import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.service.result.FcmTokenResult;
import com.faithlog.user.service.port.CurrentDeviceFcmTokenDeactivationCommand;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class FcmTokenService {

	private final FcmTokenCommandService commandService;

	public FcmTokenService(FcmTokenCommandService commandService) {
		this.commandService = commandService;
	}

	public FcmTokenResult registerToken(RegisterFcmTokenCommand command) {
		return commandService.registerToken(command);
	}

	public void deactivateToken(Long userId, Long tokenId) {
		commandService.deactivateToken(userId, tokenId);
	}

	public void deactivateCurrentDevice(CurrentDeviceFcmTokenDeactivationCommand command) {
		commandService.deactivateCurrentDevice(command);
	}

	public int deactivateStaleTokens(Instant now) {
		return commandService.deactivateStaleTokens(now);
	}
}
