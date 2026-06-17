package com.faithlog.user.infrastructure.fcm;

import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationCommand;
import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationPort;
import org.springframework.stereotype.Component;

@Component
public class NoOpCurrentDeviceFcmTokenDeactivationAdapter implements CurrentDeviceFcmTokenDeactivationPort {

	@Override
	public void deactivateCurrentDevice(CurrentDeviceFcmTokenDeactivationCommand command) {
	}
}
