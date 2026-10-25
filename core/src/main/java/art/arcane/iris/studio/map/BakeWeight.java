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

import java.awt.Color;

/**
 * How long a procedural object's bake stalls the chunk that first places it, against a server tick of 50 ms.
 */
enum BakeWeight {
    LIGHT("Light", 1D, 50D, StudioTheme.SUCCESS),
    MODERATE("Moderate", 50D, 500D, StudioTheme.WARNING),
    HEAVY("Heavy", 500D, 5_000D, StudioTheme.ERROR);

    private final String label;
    private final double fromMillis;
    private final double meterFullAtMillis;
    private final Color color;

    BakeWeight(String label, double fromMillis, double meterFullAtMillis, Color color) {
        this.label = label;
        this.fromMillis = fromMillis;
        this.meterFullAtMillis = meterFullAtMillis;
        this.color = color;
    }

    static BakeWeight of(double millis) {
        for (BakeWeight weight : values()) {
            if (millis < weight.meterFullAtMillis) {
                return weight;
            }
        }
        return HEAVY;
    }

    /**
     * How full the meter is for a bake of {@code millis}: each weight takes an equal share of the meter, filled on a
     * logarithmic scale, since bakes span thousands of times.
     */
    static double meterFill(double millis) {
        BakeWeight weight = of(millis);
        double clamped = Math.clamp(millis, weight.fromMillis, weight.meterFullAtMillis);
        double withinWeight = Math.log(clamped / weight.fromMillis) / Math.log(weight.meterFullAtMillis / weight.fromMillis);
        return (weight.ordinal() + withinWeight) / values().length;
    }

    static String scale() {
        return "Light under " + BakeTiming.formatMillis(MODERATE.fromMillis) + ", heavy from " + BakeTiming.formatMillis(HEAVY.fromMillis);
    }

    String label() {
        return label;
    }

    Color color() {
        return color;
    }
}
