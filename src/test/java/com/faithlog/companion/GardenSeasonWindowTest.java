package com.faithlog.companion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GardenSeasonWindowTest {
    @Test
    void winterStartsAtKoreaMidnightAndSpansTheYearBoundary() {
        var before = GardenExpeditionService.seasonOf(Instant.parse("2027-11-30T14:59:59Z"));
        assertThat(before.name()).isEqualTo("AUTUMN");
        var winter = GardenExpeditionService.seasonOf(Instant.parse("2027-11-30T15:00:00Z"));
        assertThat(winter.name()).isEqualTo("WINTER");
        assertThat(winter.startsOn()).isEqualTo(LocalDate.of(2027, 12, 1));
        assertThat(winter.endsOn()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(GardenExpeditionService.seasonOf(Instant.parse("2028-02-29T15:00:00Z")).name())
            .isEqualTo("SPRING");
    }
}
