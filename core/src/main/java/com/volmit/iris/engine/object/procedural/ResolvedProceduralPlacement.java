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
package com.volmit.iris.engine.object.procedural;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.CarvingMode;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.IrisObjectLimit;
import com.volmit.iris.engine.object.IrisObjectPlacement;
import com.volmit.iris.engine.object.IrisObjectRotation;
import com.volmit.iris.engine.object.IrisObjectTranslate;
import com.volmit.iris.engine.object.IrisStiltSettings;
import com.volmit.iris.engine.object.ObjectPlaceMode;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * A procedural object of a region or biome made placeable: a pool of variants baked once from its settings, and the
 * object placement they are scattered with.
 */
public abstract class ResolvedProceduralPlacement {
    private static final long VARIANT_SEED_STRIDE = 7919L;

    /** The options every procedural placement shares with an object placement. */
    public record Shape(String kind, String name, double chance, int density, long seed, int variants, ObjectPlaceMode mode,
                        IrisObjectRotation rotation, IrisObjectLimit clamp, CarvingMode carvingSupport, boolean underwater,
                        IrisObjectTranslate translate, IrisStiltSettings stiltSettings) {
    }

    private final Shape shape;
    private final IrisData data;
    private final AtomicCache<List<IrisObject>> variantCache = new AtomicCache<>();
    private final AtomicCache<IrisObjectPlacement> placementCache = new AtomicCache<>();

    protected ResolvedProceduralPlacement(Shape shape, IrisData data) {
        this.shape = shape;
        this.data = data;
    }

    /** The shape variant {@code index} is baked as, from its own seeded stream; null when it has no blocks. */
    protected abstract ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks);

    /** The number of variants to bake; the settings' count, at least one. */
    protected int variantCount() {
        return Math.max(1, shape.variants());
    }

    public String name() {
        return shape.name();
    }

    /** The type of object, as the variant keys name it: tree, ruin, formation, coral, fungus or crystal. */
    public String kind() {
        return shape.kind();
    }

    /**
     * Whether the world generator can place it. A place mode this version does not know, such as v4's CEILING_HANG,
     * reads as null, and placing such an object on the surface would put it in the wrong place.
     */
    public boolean placeable() {
        return shape.mode() != null;
    }

    /** The object placement the variants are placed with. */
    public IrisObjectPlacement placement() {
        return placementCache.aquire(this::createPlacement);
    }

    private IrisObjectPlacement createPlacement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(new KList<>(variantKeys()));
        placement.setChance(shape.chance());
        placement.setDensity(Math.max(1, shape.density()));
        placement.setMode(shape.mode() == null ? ObjectPlaceMode.CENTER_HEIGHT : shape.mode());
        placement.setCarvingSupport(shape.carvingSupport() == null ? CarvingMode.SURFACE_ONLY : shape.carvingSupport());
        placement.setUnderwater(shape.underwater());
        placement.setStiltSettings(shape.stiltSettings());
        if (shape.rotation() != null) {
            placement.setRotation(shape.rotation());
        }
        if (shape.clamp() != null) {
            placement.setClamp(shape.clamp());
        }
        if (shape.translate() != null) {
            placement.setTranslate(shape.translate());
        }
        return placement;
    }

    /** The key of every variant the settings ask for, whether or not it baked any blocks. */
    public List<String> variantKeys() {
        List<String> keys = new ArrayList<>();
        for (int index = 0; index < variantCount(); index++) {
            keys.add(variantKey(index));
        }
        return keys;
    }

    /** The key variant {@code index} is loaded under, like {@code procedural/tree/oak#0}. */
    public String variantKey(int index) {
        return "procedural/" + shape.kind() + "/" + shape.name() + "#" + index;
    }

    /** Every variant, baked on first use. */
    public List<IrisObject> variants() {
        return variantCache.aquire(() -> bake(new ObjectBuilder(data)));
    }

    /** One of the variants, picked with {@code rng}; null when none baked. */
    public IrisObject variant(RNG rng) {
        List<IrisObject> baked = variants();
        if (baked.isEmpty()) {
            return null;
        }
        return baked.get(rng.i(baked.size()));
    }


    /** Bakes every variant; the default draws variant {@code i} from {@code seed + i * stride}. */
    protected List<IrisObject> bake(ObjectBuilder blocks) {
        List<IrisObject> baked = new ArrayList<>();
        int count = variantCount();

        for (int i = 0; i < count; i++) {
            ProceduralShape generated = generate(i, variantRng(i), blocks);
            if (generated == null || generated.blocks().isEmpty()) {
                continue;
            }
            baked.add(build(blocks, variantKey(i), generated));
        }

        return baked;
    }

    protected static IrisObject build(ObjectBuilder blocks, String loadKey, ProceduralShape generated) {
        return blocks.build(loadKey, generated.width(), generated.height(), generated.depth(), generated.blocks());
    }

    protected final RNG variantRng(int index) {
        return new RNG(shape.seed() + (index * VARIANT_SEED_STRIDE));
    }

    protected final long seed() {
        return shape.seed();
    }
}
