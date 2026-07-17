package com.faithlog.notification.service.port;

public interface FcmSendPort {

	void send(FcmSendCommand command);
}
