package com.faithlog.batch.service.result;

public record DataRetentionCleanupResult(
	int notificationLogsDeleted,
	int pollResponseOptionsDeleted,
	int pollResponsesDeleted,
	int pollCommentsDeleted,
	int pollOptionsDeleted,
	int pollsDeleted,
	int softDeletedPollCommentsDeleted,
	int prayerSubmissionsDeleted,
	int devotionDailyChecksDeleted,
	int weeklyDevotionRecordsDeleted,
	int chargeItemsDeleted
) {

	public static DataRetentionCleanupResult empty() {
		return new DataRetentionCleanupResult(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	public int totalDeleted() {
		return notificationLogsDeleted
			+ pollResponseOptionsDeleted
			+ pollResponsesDeleted
			+ pollCommentsDeleted
			+ pollOptionsDeleted
			+ pollsDeleted
			+ softDeletedPollCommentsDeleted
			+ prayerSubmissionsDeleted
			+ devotionDailyChecksDeleted
			+ weeklyDevotionRecordsDeleted
			+ chargeItemsDeleted;
	}

	public DataRetentionCleanupResult plus(DataRetentionCleanupResult other) {
		return new DataRetentionCleanupResult(
			notificationLogsDeleted + other.notificationLogsDeleted,
			pollResponseOptionsDeleted + other.pollResponseOptionsDeleted,
			pollResponsesDeleted + other.pollResponsesDeleted,
			pollCommentsDeleted + other.pollCommentsDeleted,
			pollOptionsDeleted + other.pollOptionsDeleted,
			pollsDeleted + other.pollsDeleted,
			softDeletedPollCommentsDeleted + other.softDeletedPollCommentsDeleted,
			prayerSubmissionsDeleted + other.prayerSubmissionsDeleted,
			devotionDailyChecksDeleted + other.devotionDailyChecksDeleted,
			weeklyDevotionRecordsDeleted + other.weeklyDevotionRecordsDeleted,
			chargeItemsDeleted + other.chargeItemsDeleted
		);
	}
}
