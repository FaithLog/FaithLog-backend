package com.faithlog.user.infrastructure.repository;

import com.faithlog.user.domain.entity.YearlyRecapArchiveFact;
import com.faithlog.user.domain.type.YearlyRecapArchiveFactType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class YearlyRecapArchiveFactWriter {

	private static final int BATCH_SIZE = 500;

	private final YearlyRecapArchiveFactRepository repository;

	YearlyRecapArchiveFactWriter(YearlyRecapArchiveFactRepository repository) {
		this.repository = repository;
	}

	void saveMissing(YearlyRecapArchiveFactType factType, List<YearlyRecapArchiveFact> facts) {
		for (int start = 0; start < facts.size(); start += BATCH_SIZE) {
			List<YearlyRecapArchiveFact> batch = facts.subList(
				start,
				Math.min(start + BATCH_SIZE, facts.size())
			);
			List<Long> sourceIds = batch.stream()
				.map(YearlyRecapArchiveFact::sourceId)
				.toList();
			Set<Long> existing = new HashSet<>(repository.findExistingSourceIds(factType, sourceIds));
			List<YearlyRecapArchiveFact> missing = batch.stream()
				.filter(fact -> !existing.contains(fact.sourceId()))
				.toList();
			if (!missing.isEmpty()) {
				repository.saveAll(missing);
				repository.flush();
			}
		}
	}
}
