package art.arcane.iris.engine.spawn;

import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.builtin.spawn.SpawnDuration;
import art.arcane.iris.builtin.spawn.SpawnRate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpawnDurationTest {
    @Test
    public void convertsRealAndMinecraftUnitsToMilliseconds() {
        SpawnDuration duration = TestSettings.of(SpawnDuration.class, """
                {"milliseconds": 1, "minecraftTicks": 1, "seconds": 1, "minutes": 1, "minecraftHours": 1, "hours": 1,
                 "minecraftDays": 1, "minecraftWeeks": 1, "minecraftLunarCycles": 1, "days": 1}""");

        assertEquals(109_311_051L, duration.toMilliseconds());
    }

    @Test
    public void convertsMinecraftCalendarUnitsUsingTwentyMinuteDays() {
        assertEquals(1_200_000L, TestSettings.of(SpawnDuration.class, "{\"minecraftDays\": 1}").toMilliseconds());
        assertEquals(8_400_000L, TestSettings.of(SpawnDuration.class, "{\"minecraftWeeks\": 1}").toMilliseconds());
        assertEquals(9_600_000L, TestSettings.of(SpawnDuration.class, "{\"minecraftLunarCycles\": 1}").toMilliseconds());
    }

    @Test
    public void aRateSpreadsItsAmountOverItsDurationAndHasNoLimitWithoutOne() {
        SpawnRate rate = TestSettings.of(SpawnRate.class, "{\"amount\": 4, \"per\": {\"seconds\": 2}}");

        assertEquals(500L, rate.interval());
        assertFalse(rate.isInfinite());
        assertTrue(TestSettings.of(SpawnRate.class, "{\"amount\": 4}").isInfinite());
        assertEquals(1L, new SpawnRate().interval());
    }
}
