/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A procedural object's variants baked five times over, keeping the median run. The first run is slow while the JIT
 * compiles the baker, and the garbage collector or chunk generation can slow any other, so neither ends up in the middle.
 */
record BakeTiming(BakeRun median) {
    private static final int RUN_COUNT = 5;
    private static final double NANOS_PER_MILLI = 1_000_000D;

    /** Null when {@code bake} finds nothing to bake, or once the timing is no longer wanted. */
    static BakeTiming measure(Supplier<BakeRun> bake, BooleanSupplier stillWanted) {
        List<BakeRun> runs = new ArrayList<>();
        for (int run = 0; run < RUN_COUNT; run++) {
            if (!stillWanted.getAsBoolean()) {
                return null;
            }
            BakeRun measured = bake.get();
            if (measured == null) {
                return null;
            }
            runs.add(measured);
        }
        runs.sort(Comparator.comparingLong(BakeRun::totalNanos));
        return new BakeTiming(runs.get(runs.size() / 2));
    }

    double totalMillis() {
        return median.totalNanos() / NANOS_PER_MILLI;
    }

    /** How long the bake took and when world generation pays for it. */
    String explain() {
        String variants = median.variantCount() == 1 ? "1 variant" : median.variantCount() + " variants";
        return "<html>" + variants + " in " + formatMillis(totalMillis()) + " · median of " + RUN_COUNT + " runs<br>"
                + "Paid once per pack load, by the first chunk placing it<br>"
                + BakeWeight.scale() + "</html>";
    }

    static String formatMillis(double millis) {
        if (millis >= 1000D) {
            return String.format(Locale.ROOT, "%.1f s", millis / 1000D);
        }
        return String.format(Locale.ROOT, millis < 10D ? "%.1f ms" : "%.0f ms", millis);
    }
}
