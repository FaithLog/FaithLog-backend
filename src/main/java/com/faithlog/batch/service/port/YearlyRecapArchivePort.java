package com.faithlog.batch.service.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface YearlyRecapArchivePort {

	void archiveExpiredPolls(List<Long> pollIds);

	void archivePrayerSubmissionsBefore(Instant createdAtCutoff);

	void archiveAnnualRecapFacts(LocalDate startDate, LocalDate endDateExclusive);
}
