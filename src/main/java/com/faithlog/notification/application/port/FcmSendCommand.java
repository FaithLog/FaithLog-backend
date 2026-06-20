package com.faithlog.notification.application.port;

public record FcmSendCommand(
	String token,
	String title,
	String body
) {
}
