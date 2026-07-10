package com.faithlog.notification.controller.dto.request;

import com.faithlog.notification.service.command.RegisterFcmTokenCommand;
import com.faithlog.notification.domain.type.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterFcmTokenRequest(
	@NotBlank
	String token,

	@NotBlank
	@Size(max = 100)
	String clientInstanceId,

	@NotNull
	DeviceType deviceType,

	@Size(max = 50)
	String appVersion
) {

	public RegisterFcmTokenCommand toCommand(Long userId) {
		return new RegisterFcmTokenCommand(userId, token, clientInstanceId, deviceType, appVersion);
	}
}
