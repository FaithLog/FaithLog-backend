package com.faithlog.user.support;

import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationCommand;
import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationPort;
import java.util.ArrayList;
import java.util.List;

public class RecordingCurrentDeviceFcmTokenDeactivationPort implements CurrentDeviceFcmTokenDeactivationPort {

	private final List<CurrentDeviceFcmTokenDeactivationCommand> calls = new ArrayList<>();

	@Override
	public void deactivateCurrentDevice(CurrentDeviceFcmTokenDeactivationCommand command) {
		calls.add(command);
	}

	public List<CurrentDeviceFcmTokenDeactivationCommand> calls() {
		return List.copyOf(calls);
	}

	public void clear() {
		calls.clear();
	}
}
