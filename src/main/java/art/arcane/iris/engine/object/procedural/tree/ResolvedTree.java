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

package art.arcane.iris.engine.object.procedural.tree;

import art.arcane.iris.builtin.objects.ObjectBuilder;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.tree.Tree;
import art.arcane.iris.engine.object.procedural.ProceduralShape;
import art.arcane.iris.engine.object.procedural.ResolvedProceduralPlacement;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

/** A procedural tree made placeable: its variants are baked by {@link ProceduralTreeGenerator}, at heights spread between its limits. */
public final class ResolvedTree extends ResolvedProceduralPlacement {
    private static final long VARIANT_SEED_STRIDE = 7919L;
    private static final double HEIGHT_JITTER_FRACTION = 0.3;
    private static final int MINIMUM_HEIGHT = 2;

    private final Tree settings;

    public ResolvedTree(Tree settings, ObjectHost host) {
        super(new Shape(settings.name(), settings.chance(), settings.density(), settings.seed(), settings.variants(), settings.mode(),
                settings.rotation(), settings.clamp(), settings.carvingSupport(), settings.underwater(), settings.translate(),
                settings.stiltSettings().orElse(null), settings.vacuumSettings().orElse(null)), host);
        this.settings = settings;
    }

    public String getVariantLoadKey(int index) {
        return Tree.variantLoadKey(settings.name(), index);
    }

    @Override
    public boolean plausible() {
        return settings.plausible();
    }

    @Override
    public boolean tree() {
        return true;
    }

    @Override
    protected ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks) {
        throw new UnsupportedOperationException("Tree variants are baked with heights spread over the limits");
    }

    @Override
    protected List<PlaceableObject> bake(ObjectBuilder blocks) {
        List<PlaceableObject> baked = new ArrayList<>();
        int count = Math.max(1, settings.variants());
        int lo = Math.min(settings.heightMin(), settings.heightMax());
        int hi = Math.max(settings.heightMin(), settings.heightMax());
        RNG heightRng = new RNG(settings.seed());

        for (int i = 0; i < count; i++) {
            int height = variantHeight(heightRng, lo, hi, count, i);
            ProceduralShape generated = ProceduralTreeGenerator.generate(settings, Math.max(MINIMUM_HEIGHT, height),
                    new RNG(settings.seed() + (i * VARIANT_SEED_STRIDE)), blocks);
            if (generated == null || generated.blocks().isEmpty()) {
                continue;
            }
            baked.add(build(blocks, getVariantLoadKey(i), generated));
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
