package com.faithlog.notification.service.port;

public record FcmSendCommand(
	String token,
	String title,
	String body
) {
}
