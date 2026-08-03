package com.faithlog.user.service.port;

import com.faithlog.poll.domain.type.PollType;
import java.util.EnumMap;
import java.util.Map;

public record PollRecapAggregate(
	Map<PollType, Integer> participatedCounts,
	int commentCount
) {
	public PollRecapAggregate {
		EnumMap<PollType, Integer> copy = new EnumMap<>(PollType.class);
		copy.putAll(participatedCounts);
		participatedCounts = Map.copyOf(copy);
	}

	public static PollRecapAggregate empty() {
		return new PollRecapAggregate(Map.of(), 0);
	}

	public int count(PollType pollType) {
		return participatedCounts.getOrDefault(pollType, 0);
	}

	public int participatedCount() {
		return participatedCounts.values().stream().mapToInt(Integer::intValue).sum();
	}
}
