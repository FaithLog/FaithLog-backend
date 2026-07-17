package com.faithlog.user.service.port;

public interface CurrentDeviceFcmTokenDeactivationPort {

	void deactivateCurrentDevice(CurrentDeviceFcmTokenDeactivationCommand command);
}
