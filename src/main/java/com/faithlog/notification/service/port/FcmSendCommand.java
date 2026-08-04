package com.faithlog.notification.service.port;

import java.util.Map;

public record FcmSendCommand(
	String token,
	String title,
	String body,
	Map<String, String> data
) {
	public FcmSendCommand(String token, String title, String body) {
		this(token, title, body, Map.of());
	}

	public FcmSendCommand {
		data = data == null || data.isEmpty() ? Map.of() : Map.copyOf(data);
	}
}
