package com.volmit.vanilla;

import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import java.util.Objects;

/** A band of absolute world heights a platform structure is moved into, as packs write it:
 * {@code {"min": -120, "max": -20}}. Unlike other API heights these are world Y, as the platform's own structure
 * definitions use them. Reversed bounds mean the same band. A value the host creates with the no-argument constructor
 * and reads the JSON into.
 */
@Desc("An absolute world Y band a native structure is relocated into. Reversed bounds are normalized.")
public final class YBand {
    @MinNumber(-4064)
    @MaxNumber(4064)
    @Desc("Lowest absolute world Y of the band.")
    private int min = 0;

    @MinNumber(-4064)
    @MaxNumber(4064)
    @Desc("Highest absolute world Y of the band.")
    private int max = 0;

    private YBand() {
    }

    private YBand(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public static YBand of(int min, int max) {
        return new YBand(min, max);
    }

    public int lowest() {
        return Math.min(min, max);
    }

    public int highest() {
        return Math.max(min, max);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof YBand that && lowest() == that.lowest() && highest() == that.highest();
    }

    @Override
    public int hashCode() {
        return Objects.hash(lowest(), highest());
    }

    @Override
    public String toString() {
        return "YBand[" + lowest() + ".." + highest() + "]";
    }
}
