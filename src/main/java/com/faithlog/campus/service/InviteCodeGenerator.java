package com.faithlog.campus.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class InviteCodeGenerator {

	private static final char[] SYMBOLS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
	private static final int RANDOM_LENGTH = 8;

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		StringBuilder builder = new StringBuilder("FL-");
		for (int index = 0; index < RANDOM_LENGTH; index++) {
			builder.append(SYMBOLS[secureRandom.nextInt(SYMBOLS.length)]);
		}
		return builder.toString();
	}
}
