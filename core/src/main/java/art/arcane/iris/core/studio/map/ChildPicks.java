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

package art.arcane.iris.core.studio.map;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.world.biome.IrisBiome;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The child picks the generator makes from a biome at a spot, parent first; the last one settled the spot's biome. */
final class ChildPicks {
    private ChildPicks() {
    }

    // A parent that keeps the spot ends the walk: every level below would read the same noise and keep it again.
    static List<ChildPick> from(IrisComplex complex, IrisBiome biome, double x, double z) {
        List<ChildPick> picks = new ArrayList<>();
        IrisBiome parent = biome;
        for (int level = 0; level < IrisComplex.CHILD_PICK_LEVELS && !parent.getChildren().isEmpty(); level++) {
            List<IrisBiome> slots = complex.childSlots(parent);
            if (slots.isEmpty()) {
                break;
            }
            double noise = complex.childNoise(parent).noiseFast2D(x, z);
            IrisBiome picked = slots.get(slotIndex(noise, slots.size())).withInferredType(parent.getInferredType());
            ChildPick pick = new ChildPick(parent, slots, noise, picked);
            picks.add(pick);
            if (pick.kept()) {
                break;
            }
            parent = picked;
        }
        return picks;
    }

    /** The noise that settled the spot after the children: the last child pick's, or the given one when none picked. */
    static double settlingNoise(IrisComplex complex, IrisBiome biome, double x, double z, double listNoise) {
        List<ChildPick> picks = from(complex, biome, x, z);
        return picks.isEmpty() ? listNoise : picks.getLast().noise();
    }

    /** The slot the child noise picks, rounded the way {@code CNG.fit2D} rounds. */
    static int slotIndex(double noise, int slotCount) {
        if (slotCount == 1) {
            return 0;
        }
        long rounded = Math.round(noise * (slotCount - 1));
        return (int) Math.clamp(rounded, 0, slotCount - 1);
    }

    /** One level of children: the parent's slots, the noise that read them, and the biome it picked. */
    record ChildPick(IrisBiome parent, List<IrisBiome> slots, double noise, IrisBiome picked) {
        boolean kept() {
            return Objects.equals(picked.getLoadKey(), parent.getLoadKey());
        }
    }
}
