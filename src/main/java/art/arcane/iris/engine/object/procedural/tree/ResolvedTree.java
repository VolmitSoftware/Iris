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

import art.arcane.iris.builtin.objects.tree.Tree;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.procedural.ResolvedProceduralPlacement;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.io.File;

/** A procedural tree made placeable: its variants are baked by {@link ProceduralTreeGenerator}, at heights spread between its limits. */
public final class ResolvedTree extends ResolvedProceduralPlacement {
    private static final long VARIANT_SEED_STRIDE = 7919L;
    private static final double HEIGHT_JITTER_FRACTION = 0.3;
    private static final int MINIMUM_HEIGHT = 2;

    private final Tree settings;

    public ResolvedTree(Tree settings) {
        super(new Shape(settings.name(), settings.chance(), settings.density(), settings.seed(), settings.variants(), settings.mode(),
                settings.rotation(), settings.clamp(), settings.carvingSupport(), settings.underwater(), settings.translate(),
                settings.stiltSettings().orElse(null), settings.vacuumSettings().orElse(null)));
        this.settings = settings;
    }

    /** The key a variant is loaded under, which placement markers and the sapling tree list name. */
    public static String variantLoadKey(String name, int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        return "procedural/tree/" + name + "#" + index;
    }

    public String getVariantLoadKey(int index) {
        return variantLoadKey(settings.name(), index);
    }

    public Tree settings() {
        return settings;
    }

    @Override
    public boolean isPlausible() {
        return settings.plausible();
    }

    @Override
    protected IrisObject generate(int index, RNG rng, IrisData data) {
        throw new UnsupportedOperationException("Tree variants are baked with heights spread over the limits");
    }

    @Override
    protected KList<IrisObject> bake(IrisData data) {
        KList<IrisObject> baked = new KList<>();
        int count = Math.max(1, settings.variants());
        int lo = Math.min(settings.heightMin(), settings.heightMax());
        int hi = Math.max(settings.heightMin(), settings.heightMax());
        RNG heightRng = new RNG(settings.seed());

        for (int i = 0; i < count; i++) {
            int height = variantHeight(heightRng, lo, hi, count, i);
            IrisObject object = ProceduralTreeGenerator.generate(settings, Math.max(MINIMUM_HEIGHT, height),
                    new RNG(settings.seed() + (i * VARIANT_SEED_STRIDE)), data);
            if (object == null || object.getBlocks().isEmpty()) {
                continue;
            }
            object.setLoadKey(getVariantLoadKey(i));
            object.setLoader(data);
            baked.add(object);
        }

        digestForGoldenDebug(baked);
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

    private static void digestForGoldenDebug(KList<IrisObject> baked) {
        if (!new File("plugins/Iris/goldendebug.txt").isFile()) {
            return;
        }
        for (IrisObject object : baked) {
            long[] digest = new long[]{0L};
            object.getBlocks().forEach((vector, state) -> {
                long position = ((long) vector.getBlockX() << 40) ^ ((long) vector.getBlockY() << 20) ^ vector.getBlockZ();
                digest[0] ^= Long.rotateLeft(position * 0x9E3779B97F4A7C15L ^ state.key().hashCode(), (int) (position & 63));
            });
            IrisLogging.debug("Goldendebug bake: " + object.getLoadKey() + " blocks=" + object.getBlocks().size() + " digest=" + Long.toHexString(digest[0]));
        }
    }
}
