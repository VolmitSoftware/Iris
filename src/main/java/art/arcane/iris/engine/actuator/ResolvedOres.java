/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.actuator;

import art.arcane.iris.builtin.terrain.OreGenerator;
import art.arcane.iris.builtin.terrain.OreSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.PlatformBlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * The ore generators of one dimension, region or biome made ready ({@link ResolvedOre}), in the order its settings list them
 * and split into the ones that also replace surface blocks and the ones that only replace rock. A level without ores is
 * {@link #NONE}.
 */
final class ResolvedOres {
    static final ResolvedOres NONE = new ResolvedOres(List.of());

    private final List<ResolvedOre> all;
    private final List<ResolvedOre> surface;
    private final List<ResolvedOre> underground;
    private final YBand surfaceBand;
    private final YBand undergroundBand;

    ResolvedOres(List<ResolvedOre> all) {
        this.all = List.copyOf(all);
        this.surface = this.all.stream().filter(ResolvedOre::generatesSurface).toList();
        this.underground = this.all.stream().filter(ore -> !ore.generatesSurface()).toList();
        this.surfaceBand = YBand.of(surface);
        this.undergroundBand = YBand.of(underground);
    }

    /** {@code settings} ready for {@code engine}; generators the pack wrote as {@code null} are left out. */
    static ResolvedOres of(OreSettings settings, Engine engine) {
        List<ResolvedOre> ores = new ArrayList<>();
        for (OreGenerator generator : settings.generators()) {
            if (generator != null) {
                ores.add(ResolvedOre.of(generator, engine));
            }
        }
        return ores.isEmpty() ? NONE : new ResolvedOres(ores);
    }

    /** The heights where any surface ore can generate. */
    YBand surfaceBand() {
        return surfaceBand;
    }

    /** The heights where any rock-only ore can generate. */
    YBand undergroundBand() {
        return undergroundBand;
    }

    /** Warms every ore in the order the settings list them, each with the next seed. */
    void warm(LongSupplier seeds) {
        for (ResolvedOre ore : all) {
            ore.warm(seeds.getAsLong());
        }
    }

    PlatformBlockState generateSurface(int x, int y, int z) {
        return firstOre(surface, x, y, z);
    }

    PlatformBlockState generateUnderground(int x, int y, int z) {
        return firstOre(underground, x, y, z);
    }

    private static PlatformBlockState firstOre(List<ResolvedOre> ores, int x, int y, int z) {
        for (int index = 0; index < ores.size(); index++) {
            PlatformBlockState ore = ores.get(index).generate(x, y, z);
            if (ore != null) {
                return ore;
            }
        }
        return null;
    }

    /** The lowest and highest height any of a set of ores reaches; empty when there are none. */
    record YBand(boolean hasOres, double min, double max) {
        static final YBand EMPTY = new YBand(false, 0, 0);

        static YBand of(List<ResolvedOre> ores) {
            if (ores.isEmpty()) {
                return EMPTY;
            }
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (ResolvedOre ore : ores) {
                min = Math.min(min, ore.minY());
                max = Math.max(max, ore.maxY());
            }
            return new YBand(true, min, max);
        }

        boolean contains(int y) {
            return hasOres && y >= min && y <= max;
        }
    }
}
