package com.faithlog.weeklymaterial.infrastructure.adapter;

import com.faithlog.weeklymaterial.service.port.WeeklyMaterialSlotLockPort;
import com.faithlog.weeklymaterial.infrastructure.repository.WeeklyMaterialGlobalLockRepository;
import org.springframework.stereotype.Component;

@Component
public class WeeklyMaterialSlotLockAdapter implements WeeklyMaterialSlotLockPort {
	private final WeeklyMaterialGlobalLockRepository locks;
	public WeeklyMaterialSlotLockAdapter(WeeklyMaterialGlobalLockRepository locks) { this.locks = locks; }
	@Override
	public void lockGlobal() {
		locks.findSingletonForUpdate()
			.orElseThrow(() -> new IllegalStateException("weekly material global lock is missing"));
	}
}
