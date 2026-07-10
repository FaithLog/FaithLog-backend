package com.faithlog.campus.service.port;

public record CampusUserLookupResult(
	Long userId,
	String name,
	String email,
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
