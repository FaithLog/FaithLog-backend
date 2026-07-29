package com.faithlog.user.service;

import java.util.Locale;

final class EmailNormalizer {

	private EmailNormalizer() {
	}

	static String normalize(String email) {
		return storageValue(email).toLowerCase(Locale.ROOT);
	}

	static String storageValue(String email) {
		return email.trim();
	}
}
