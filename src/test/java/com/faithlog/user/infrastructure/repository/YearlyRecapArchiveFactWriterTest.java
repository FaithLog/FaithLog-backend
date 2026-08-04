package com.faithlog.user.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.user.domain.entity.YearlyRecapArchiveFact;
import com.faithlog.user.domain.type.YearlyRecapArchiveFactType;
import java.util.Collection;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YearlyRecapArchiveFactWriterTest {

	@Mock private YearlyRecapArchiveFactRepository repository;

	@Test
	void large_annual_archive_uses_bounded_lookup_and_write_batches() {
		List<YearlyRecapArchiveFact> facts = LongStream.rangeClosed(1, 1_001)
			.mapToObj(id -> YearlyRecapArchiveFact.comment(id, id, 2026, 1L))
			.toList();
		when(repository.findExistingSourceIds(eq(YearlyRecapArchiveFactType.COMMENT), any()))
			.thenReturn(List.of(2L), List.of(502L), List.of(1_001L));
		YearlyRecapArchiveFactWriter writer = new YearlyRecapArchiveFactWriter(repository);

		writer.saveMissing(YearlyRecapArchiveFactType.COMMENT, facts);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Collection<Long>> lookupCaptor = ArgumentCaptor.forClass(Collection.class);
		verify(repository, times(3)).findExistingSourceIds(
			eq(YearlyRecapArchiveFactType.COMMENT), lookupCaptor.capture());
		assertThat(lookupCaptor.getAllValues())
			.hasSize(3)
			.allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(500));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Iterable<YearlyRecapArchiveFact>> saveCaptor = ArgumentCaptor.forClass(Iterable.class);
		verify(repository, times(2)).saveAll(saveCaptor.capture());
		assertThat(saveCaptor.getAllValues())
			.allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(500));
		verify(repository, times(2)).flush();
	}
}
