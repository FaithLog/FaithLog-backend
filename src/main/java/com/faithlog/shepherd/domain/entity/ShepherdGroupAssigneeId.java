package com.faithlog.shepherd.domain.entity;

import java.io.Serializable;
import java.util.Objects;

public class ShepherdGroupAssigneeId implements Serializable {
	private Long shepherdGroupId;
	private Long userId;

	protected ShepherdGroupAssigneeId() {
	}

	public ShepherdGroupAssigneeId(Long shepherdGroupId, Long userId) {
		this.shepherdGroupId = shepherdGroupId;
		this.userId = userId;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (!(object instanceof ShepherdGroupAssigneeId that)) {
			return false;
		}
		return Objects.equals(shepherdGroupId, that.shepherdGroupId)
			&& Objects.equals(userId, that.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(shepherdGroupId, userId);
	}
}
