package com.faithlog.user.application.port;

public interface CurrentDeviceFcmTokenDeactivationPort {

	void deactivateCurrentDevice(CurrentDeviceFcmTokenDeactivationCommand command);
}
