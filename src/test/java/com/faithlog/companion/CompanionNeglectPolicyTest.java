package com.faithlog.companion;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.faithlog.companion.CompanionNeglectPolicy.Meters;
import com.faithlog.companion.CompanionNeglectPolicy.Snapshot;

class CompanionNeglectPolicyTest {
    private static final Instant START = Instant.parse("2026-11-17T00:00:00Z");
    private static Snapshot full() {
        return new Snapshot(new Meters(100, 100, 100, 100, 100), START, START, 0);
    }

    @Test void complete_hours_only_and_repeated_queries_do_not_reset_fractional_time() {
        var before = CompanionNeglectPolicy.evaluate(full(), START.plusSeconds(3599));
        assertThat(before).isEqualTo(full());
        var atThree = CompanionNeglectPolicy.evaluate(before, START.plus(Duration.ofMinutes(239)));
        assertThat(atThree.elapsedHours()).isEqualTo(3);
        var atFour = CompanionNeglectPolicy.evaluate(atThree, START.plus(Duration.ofMinutes(241)));
        assertThat(atFour.elapsedHours()).isEqualTo(4);
        assertThat(atFour.meters().satiety()).isEqualTo(97);
    }

    @Test void neglect_reduces_four_meters_but_healthy_pet_keeps_health() {
        var after = CompanionNeglectPolicy.evaluate(full(), START.plus(Duration.ofHours(72)));
        assertThat(after.meters()).isEqualTo(new Meters(46, 76, 82, 98, 100));
        assertThat(CompanionNeglectPolicy.evaluate(after, START.plus(Duration.ofHours(72))))
                .isEqualTo(after);
    }

    @Test void health_decreases_when_a_care_meter_falls_below_25_at_12_hour_tick() {
        var at96 = CompanionNeglectPolicy.evaluate(full(), START.plus(Duration.ofHours(96)));
        assertThat(at96.meters().health()).isEqualTo(100);
        var at108 = CompanionNeglectPolicy.evaluate(at96, START.plus(Duration.ofHours(108)));
        assertThat(at108.meters().satiety()).isEqualTo(19);
        assertThat(at108.meters().health()).isEqualTo(98);
    }

    @Test void frequent_reads_equal_one_large_read() {
        var all = CompanionNeglectPolicy.evaluate(full(), START.plus(Duration.ofHours(256)));
        Snapshot piecewise = full();
        for (int hour = 1; hour <= 256; hour++) {
            piecewise = CompanionNeglectPolicy.evaluate(piecewise, START.plus(Duration.ofHours(hour)));
        }
        assertThat(piecewise).isEqualTo(all);
    }

    @Test void real_care_resets_affection_grace_without_magically_restoring_food() {
        var care = CompanionNeglectPolicy.markCaredFor(full(), START.plus(Duration.ofHours(47)));
        var at96 = CompanionNeglectPolicy.evaluate(care, START.plus(Duration.ofHours(96)));
        assertThat(at96.meters().affection()).isEqualTo(100);
        var at119 = CompanionNeglectPolicy.evaluate(at96, START.plus(Duration.ofHours(119)));
        assertThat(at119.meters().affection()).isEqualTo(98);
        assertThat(at119.meters().satiety()).isLessThan(100);
    }

    @Test void long_absence_never_results_in_negative_stats_or_lost_level() {
        var neglected = CompanionNeglectPolicy.evaluate(full(), START.plus(Duration.ofDays(365)));
        assertThat(neglected.meters()).isEqualTo(new Meters(0, 0, 0, 0, 0));
        var growth = neglected.growthState(2, 1040, 12);
        assertThat(growth.level()).isEqualTo(2);
        assertThat(growth.growthXp()).isEqualTo(1040);
        assertThat(CompanionGrowthPolicy.evaluate(growth).canEvolve()).isFalse();
    }

    @Test void backwards_clock_can_never_restore_stats() {
        var later = CompanionNeglectPolicy.evaluate(full(), START.plus(Duration.ofHours(12)));
        assertThat(CompanionNeglectPolicy.evaluate(later, START)).isEqualTo(later);
        assertThatThrownBy(() -> CompanionNeglectPolicy.markCaredFor(later, START))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void invalid_meters_and_counters_are_rejected() {
        assertThatThrownBy(() -> new Meters(-1, 100, 100, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Meters(100, 101, 100, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Snapshot(full().meters(), START, START, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
