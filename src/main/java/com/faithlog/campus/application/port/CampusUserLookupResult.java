package com.faithlog.campus.application.port;

public record CampusUserLookupResult(
	Long userId,
	String role,
	boolean active
) {

	public boolean canCreateCampus() {
		return isManager() || isAdmin();
	}

	public boolean isAdmin() {
		return "ADMIN".equals(role);
	}

	private boolean isManager() {
		return "MANAGER".equals(role);
	}
}
