package com.faithlog.notification.service;

public record NotificationDeduplicationReservation(
	NotificationDeduplicationKey key,
	String ownerToken
) {
}
