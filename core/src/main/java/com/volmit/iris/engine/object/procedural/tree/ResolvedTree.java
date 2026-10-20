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

package com.volmit.iris.engine.object.procedural.tree;

import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.procedural.ProceduralShape;
import com.volmit.iris.engine.object.procedural.ResolvedProceduralPlacement;
import com.volmit.iris.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

/** A procedural tree made placeable: its variants are baked by {@link ProceduralTreeGenerator}, at heights spread between its limits. */
public final class ResolvedTree extends ResolvedProceduralPlacement {
    private static final long VARIANT_SEED_STRIDE = 7919L;
    private static final double HEIGHT_JITTER_FRACTION = 0.3;
    private static final int MINIMUM_HEIGHT = 2;
    /** Nearly three times the tallest tree in the packs. Taller settings are clamped so a typo bakes in seconds, not hours. */
    static final int TALLEST_TREE_BLOCKS = 128;

    private final Tree settings;

    public ResolvedTree(Tree settings, IrisData data) {
        super(new Shape("trees", settings.name(), settings.chance(), settings.density(), settings.seed(), settings.variants(), settings.mode(),
                settings.rotation(), settings.clamp(), settings.carvingSupport(), settings.underwater(), settings.translate(),
                settings.stiltSettings().orElse(null)), data);
        this.settings = settings;
    }




    @Override
    protected ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks) {
        throw new UnsupportedOperationException("Tree variants are baked with heights spread over the limits");
    }

    @Override
    protected List<IrisObject> bake(ObjectBuilder blocks) {
        List<IrisObject> baked = new ArrayList<>();
        int count = Math.max(1, settings.variants());
        int lo = Math.min(Math.min(settings.heightMin(), settings.heightMax()), TALLEST_TREE_BLOCKS);
        int hi = Math.min(Math.max(settings.heightMin(), settings.heightMax()), TALLEST_TREE_BLOCKS);
        RNG heightRng = new RNG(settings.seed());

        for (int i = 0; i < count; i++) {
            int height = variantHeight(heightRng, lo, hi, count, i);
            ProceduralShape generated = ProceduralTreeGenerator.generate(settings, Math.max(MINIMUM_HEIGHT, height),
                    new RNG(settings.seed() + (i * VARIANT_SEED_STRIDE)), blocks);
            if (generated == null || generated.blocks().isEmpty()) {
                continue;
            }
            baked.add(build(blocks, variantKey(i), generated));
        }

        return baked;
    }

    private static int variantHeight(RNG heightRng, int lo, int hi, int count, int index) {
        if (count == 1 || lo == hi) {
            return heightRng.i(lo, hi + 1);
        }
        double step = (hi - lo) / (double) (count - 1);
        double base = lo + step * index;
        double jitter = heightRng.d(-step * HEIGHT_JITTER_FRACTION, step * HEIGHT_JITTER_FRACTION);
        return (int) Math.round(Math.max(lo, Math.min(hi, base + jitter)));
    }
}
