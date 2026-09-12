package art.arcane.iris.pack.value;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisDurationTest {
    @Test
    public void convertsRealAndMinecraftUnitsToMilliseconds() {
        IrisDuration duration = new IrisDuration();
        duration.setMilliseconds(1);
        duration.setMinecraftTicks(1);
        duration.setSeconds(1);
        duration.setMinutes(1);
        duration.setMinecraftHours(1);
        duration.setHours(1);
        duration.setMinecraftDays(1);
        duration.setMinecraftWeeks(1);
        duration.setMinecraftLunarCycles(1);
        duration.setDays(1);

        assertEquals(109_311_051L, duration.toMilliseconds());
    }

    @Test
    public void convertsMinecraftCalendarUnitsUsingTwentyMinuteDays() {
        IrisDuration day = new IrisDuration();
        IrisDuration week = new IrisDuration();
        IrisDuration lunarCycle = new IrisDuration();
        day.setMinecraftDays(1);
        week.setMinecraftWeeks(1);
        lunarCycle.setMinecraftLunarCycles(1);

        assertEquals(1_200_000L, day.toMilliseconds());
        assertEquals(8_400_000L, week.toMilliseconds());
        assertEquals(9_600_000L, lunarCycle.toMilliseconds());
    }
}
