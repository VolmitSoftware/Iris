package com.volmit.iris.scaffold.lighting;

import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Just some utilities used by Light Cleaner
 */
public class LightingUtil {
    private static TimeDurationFormat timeFormat_hh_mm = new TimeDurationFormat("HH 'hours' mm 'minutes'");
    private static TimeDurationFormat timeFormat_mm_ss = new TimeDurationFormat("mm 'minutes' ss 'seconds'");

    private static final long SECOND_MILLIS = 1000L;
    private static final long MINUTE_MILLIS = 60L * SECOND_MILLIS;
    private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;

    public static String formatDuration(long duration) {
        if (duration < MINUTE_MILLIS) {
            return MathUtil.round((double) duration / (double) SECOND_MILLIS, 2) + " seconds";
        } else if (duration < HOUR_MILLIS) {
            return timeFormat_mm_ss.format(duration);
        } else if (duration < (2*DAY_MILLIS)) {
            return timeFormat_hh_mm.format(duration);
        } else {
            long num_days = duration / DAY_MILLIS;
            long num_hours = (duration % DAY_MILLIS) / HOUR_MILLIS;
            return num_days + " days " + num_hours + " hours";
        }
    }
}
