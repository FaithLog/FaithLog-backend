package com.faithlog.companion;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import com.faithlog.companion.CompanionExperienceDecayPolicy.Experience;
import com.faithlog.companion.CompanionNeglectPolicy.Meters;
import com.faithlog.companion.CompanionNeglectPolicy.Snapshot;

class CompanionExperienceDecayPolicyTest {
    private static final Instant START = Instant.parse("2026-11-18T00:00:00Z");

    private static Snapshot fullMeters() {
        return new Snapshot(new Meters(100, 100, 100, 100, 100), START, START, 0);
    }

    private static Experience xp(int level, long amount) {
        return new Experience(level, amount, amount, START, START);
    }

    @Test void grace_is_48_hours_and_first_loss_is_at_72_hours() {
        var before = CompanionExperienceDecayPolicy.evaluate(
                xp(2, 750), fullMeters(), START.plus(Duration.ofHours(71)));
        assertThat(before.experience().growthXp()).isEqualTo(750);
        assertThat(before.lostXp()).isZero();

        var at72 = CompanionExperienceDecayPolicy.evaluate(
                before.experience(), before.care(), START.plus(Duration.ofHours(72)));
        assertThat(at72.experience().growthXp()).isEqualTo(740);
        assertThat(at72.lostXp()).isEqualTo(10);
    }

    @Test void danger_penalty_starts_only_on_the_first_dangerous_day() {
        var at96 = CompanionExperienceDecayPolicy.evaluate(
                xp(2, 750), fullMeters(), START.plus(Duration.ofHours(96)));
        assertThat(at96.experience().growthXp()).isEqualTo(730);
        assertThat(at96.lostXp()).isEqualTo(20);
        var at120 = CompanionExperienceDecayPolicy.evaluate(
                at96.experience(), at96.care(), START.plus(Duration.ofHours(120)));
        assertThat(at120.experience().growthXp()).isEqualTo(710);
        assertThat(at120.lostXp()).isEqualTo(20);
        assertThat(at120.care().meters().satiety()).isLessThan(25);
    }

    @Test void many_small_reads_match_a_single_read_and_never_recharge_loss() {
        var oneShot = CompanionExperienceDecayPolicy.evaluate(
                xp(2, 950), fullMeters(), START.plus(Duration.ofDays(16)));
        var current = new CompanionExperienceDecayPolicy.Result(xp(2, 950), fullMeters(), 0);
        for (int hour = 1; hour <= 16 * 24; hour++) {
            current = CompanionExperienceDecayPolicy.evaluate(
                    current.experience(), current.care(), START.plus(Duration.ofHours(hour)));
        }
        assertThat(current.experience()).isEqualTo(oneShot.experience());
        assertThat(current.care()).isEqualTo(oneShot.care());
        assertThat(CompanionExperienceDecayPolicy.evaluate(
                current.experience(), current.care(), START.plus(Duration.ofDays(16))).lostXp())
                .isZero();
    }

    @Test void preserves_stage_floor_and_total_earned_experience() {
        var level1 = CompanionExperienceDecayPolicy.evaluate(
                xp(1, 120), fullMeters(), START.plus(Duration.ofDays(365)));
        var level2 = CompanionExperienceDecayPolicy.evaluate(
                xp(2, 750), fullMeters(), START.plus(Duration.ofDays(365)));
        var level3 = CompanionExperienceDecayPolicy.evaluate(
                xp(3, 1200), fullMeters(), START.plus(Duration.ofDays(365)));
        assertThat(level1.experience().growthXp()).isZero();
        assertThat(level2.experience().growthXp()).isEqualTo(300);
        assertThat(level3.experience().growthXp()).isEqualTo(1000);
        assertThat(level2.experience().totalEarnedXp()).isEqualTo(750);
        assertThat(level3.experience().level()).isEqualTo(3);
        assertThat(level2.care().meters().health()).isZero();
    }

    @Test void care_settles_old_loss_then_restarts_grace_without_refunding_xp() {
        var at72 = CompanionExperienceDecayPolicy.markCaredFor(
                xp(2, 750), fullMeters(), START.plus(Duration.ofHours(72)));
        assertThat(at72.lostXp()).isEqualTo(10);
        assertThat(at72.experience().growthXp()).isEqualTo(740);
        assertThat(at72.experience().lastCaredAt()).isEqualTo(START.plus(Duration.ofHours(72)));
        var beforeNext = CompanionExperienceDecayPolicy.evaluate(
                at72.experience(), at72.care(), START.plus(Duration.ofHours(143)));
        assertThat(beforeNext.lostXp()).isZero();
        var nextDay = CompanionExperienceDecayPolicy.evaluate(
                beforeNext.experience(), beforeNext.care(), START.plus(Duration.ofHours(144)));
        // At this point satiety has declined to zero: first post-care XP tick is severe.
        assertThat(nextDay.lostXp()).isEqualTo(20);
        assertThat(nextDay.experience().growthXp()).isEqualTo(720);
    }

    @Test void partial_hours_and_clock_rollback_are_safe() {
        var origin = START.plus(Duration.ofMinutes(15));
        var ex = new Experience(1, 250, 300, START, origin);
        var stats = new Snapshot(new Meters(100, 100, 100, 100, 100), START, origin, 0);
        var at72 = CompanionExperienceDecayPolicy.evaluate(ex, stats,
                origin.plus(Duration.ofHours(72)));
        assertThat(at72.lostXp()).isEqualTo(10);
        var at73 = CompanionExperienceDecayPolicy.evaluate(
                at72.experience(), at72.care(), START.plus(Duration.ofHours(73)));
        assertThat(at73.lostXp()).isZero();
        var rolledBack = CompanionExperienceDecayPolicy.evaluate(
                at73.experience(), at73.care(), START);
        assertThat(rolledBack.experience()).isEqualTo(at73.experience());
        assertThat(CompanionExperienceDecayPolicy.evaluate(
                at73.experience(), at73.care(), START.plus(Duration.ofHours(73))).lostXp())
                .isZero();
    }

    @Test void care_rewards_increase_both_counters_but_decayed_xp_is_not_refunded() {
        var decayed = CompanionExperienceDecayPolicy.evaluate(
                xp(2, 750), fullMeters(), START.plus(Duration.ofHours(72)));
        var earned = CompanionExperienceDecayPolicy.awardEarnedXp(decayed.experience(), 15);
        assertThat(earned.growthXp()).isEqualTo(755);
        assertThat(earned.totalEarnedXp()).isEqualTo(765);
        assertThatThrownBy(() -> CompanionExperienceDecayPolicy.awardEarnedXp(earned, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void evolution_uses_decreased_xp_and_current_meter_thresholds() {
        var ready = new Experience(1, 300, 300, START, START);
        assertThat(CompanionExperienceDecayPolicy.evolve(ready, fullMeters(), 3).level())
                .isEqualTo(2);
        var insufficient = CompanionExperienceDecayPolicy.evaluate(
                ready, fullMeters(), START.plus(Duration.ofHours(72)));
        assertThatThrownBy(() -> CompanionExperienceDecayPolicy.evolve(
                insufficient.experience(), insufficient.care(), 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void mismatched_state_or_invalid_floor_is_rejected() {
        var care = fullMeters();
        var shifted = new Experience(2, 300, 700, START.plusSeconds(3600), START);
        assertThatThrownBy(() -> CompanionExperienceDecayPolicy.evaluate(shifted, care, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Experience(2, 299, 500, START, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Experience(3, 1000, 999, START, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompanionExperienceDecayPolicy.floorForLevel(4))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
