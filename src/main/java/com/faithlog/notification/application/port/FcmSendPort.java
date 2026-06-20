package com.faithlog.notification.application.port;

public interface FcmSendPort {

	void send(FcmSendCommand command);
}
