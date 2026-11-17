package com.faithlog.companion;

import static org.assertj.core.api.Assertions.*;
import com.faithlog.companion.CompanionGrowthPolicy.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CompanionGrowthPolicyTest {

    @Test void first_evolution_requires_xp_distinct_days_and_healthy_meters() {
        var state = new State(1, 299, 2, 59, 49, 49);
        var result = CompanionGrowthPolicy.evaluate(state);
        assertThat(result.nextLevel()).isEqualTo(2);
        assertThat(result.blockers()).containsExactly(
                Blocker.EXPERIENCE, Blocker.CARE_DAYS,
                Blocker.HEALTH, Blocker.SATIETY, Blocker.CLEANLINESS);
        assertThat(result.canEvolve()).isFalse();
        assertThatThrownBy(() -> CompanionGrowthPolicy.evolve(state))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void level_one_to_two_at_exact_threshold_is_explicit_and_preserves_state() {
        var state = new State(1, 300, 3, 60, 50, 50);
        assertThat(CompanionGrowthPolicy.evaluate(state).canEvolve()).isTrue();
        assertThat(CompanionGrowthPolicy.evolve(state))
                .isEqualTo(new State(2, 300, 3, 60, 50, 50));
    }

    @Test void second_evolution_needs_larger_threshold_and_preserves_costume_independently() {
        var insufficient = new State(2, 999, 10, 70, 60, 60);
        assertThat(CompanionGrowthPolicy.evaluate(insufficient).blockers())
                .containsExactly(Blocker.EXPERIENCE);
        var ready = new State(2, 1000, 10, 70, 60, 60);
        assertThat(CompanionGrowthPolicy.evaluate(ready).canEvolve()).isTrue();
        assertThat(CompanionGrowthPolicy.evolve(ready).level()).isEqualTo(3);
    }

    @Test void final_evolution_is_idempotently_blocked_and_has_no_next_level() {
        var state = new State(3, 9000, 100, 100, 100, 100);
        var decision = CompanionGrowthPolicy.evaluate(state);
        assertThat(decision.blockers()).containsExactly(Blocker.MAX_LEVEL);
        assertThat(decision.canEvolve()).isFalse();
        assertThatThrownBy(() -> CompanionGrowthPolicy.evolve(state))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void state_validation_prevents_corrupt_numbers() {
        assertThatThrownBy(() -> new State(0, 0, 0, 100, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new State(4, 0, 0, 100, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new State(1, -1, 0, 100, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new State(1, 0, 0, 101, 100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new State(1, 0, 0, 100, -1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void care_awards_obey_daily_cap_and_sleep_grants_no_xp() {
        assertThat(CompanionGrowthPolicy.growthXpAward(0, CareAction.FEED)).isEqualTo(15);
        assertThat(CompanionGrowthPolicy.growthXpAward(95, CareAction.PLAY)).isEqualTo(5);
        assertThat(CompanionGrowthPolicy.growthXpAward(100, CareAction.CLEAN)).isZero();
        assertThat(CompanionGrowthPolicy.growthXpAward(0, CareAction.SLEEP)).isZero();
        assertThatThrownBy(() -> CompanionGrowthPolicy.growthXpAward(-1, CareAction.PET))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void cooldown_requires_elapsed_time_and_rejects_future_timestamps() {
        var start = Instant.parse("2026-11-17T00:00:00Z");
        assertThat(CompanionGrowthPolicy.cooldownElapsed(start, null, CareAction.FEED))
                .isTrue();
        assertThat(CompanionGrowthPolicy.cooldownElapsed(start.plusSeconds(14_399), start, CareAction.FEED))
                .isFalse();
        assertThat(CompanionGrowthPolicy.cooldownElapsed(start.plusSeconds(14_400), start, CareAction.FEED))
                .isTrue();
        assertThat(CompanionGrowthPolicy.cooldownElapsed(start.minusSeconds(1), start, CareAction.FEED))
                .isFalse();
    }

    @Test void zero_health_does_not_permanently_delete_any_companion() {
        var fainted = new State(1, 500, 8, 0, 0, 0);
        assertThat(CompanionGrowthPolicy.evaluate(fainted).blockers())
                .contains(Blocker.HEALTH, Blocker.SATIETY, Blocker.CLEANLINESS);
        assertThat(fainted.level()).isEqualTo(1);
    }
}
