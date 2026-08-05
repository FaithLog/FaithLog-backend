package com.faithlog.weeklymaterial.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "weekly_material_global_lock")
public class WeeklyMaterialGlobalLock {
	@Id
	private Short id;

	protected WeeklyMaterialGlobalLock() {
	}

	private WeeklyMaterialGlobalLock(Short id) {
		this.id = id;
	}

	public static WeeklyMaterialGlobalLock singleton() {
		return new WeeklyMaterialGlobalLock((short) 1);
	}

	public Short id() {
		return id;
	}
}
