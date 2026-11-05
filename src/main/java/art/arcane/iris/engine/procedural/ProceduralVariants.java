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

package art.arcane.iris.engine.procedural;

import art.arcane.iris.modules.objects.ObjectBuilder;
import art.arcane.iris.modules.objects.ObjectSize;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The pool of variants one procedural definition bakes, placed through the object key {@code procedural/<path>}: a
 * placement picks one variant per object it places, or always the same one with {@code procedural/<path>#<index>}.
 */
public abstract class ProceduralVariants {
    private static final long VARIANT_SEED_STRIDE = 7919L;

    private final String path;
    private final long seed;
    private final int variantCount;
    private final PackResources resources;
    private volatile List<PlaceableObject> baked;

    protected ProceduralVariants(String path, long seed, int variantCount, PackResources resources) {
        this.path = path;
        this.seed = seed;
        this.variantCount = Math.max(1, variantCount);
        this.resources = resources;
    }

    /** The shape variant {@code index} is baked as, from its own seeded stream; null when it has no blocks. */
    protected abstract ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks, BakeProfile profile);

    /** Where the pack keeps the definition, under {@code procedural/}, like {@code trees/oak}. */
    public String path() {
        return path;
    }

    /** The key placements name the whole pool by, like {@code procedural/trees/oak}. */
    public String key() {
        return ProceduralObjectCatalog.KEY_PREFIX + path;
    }

    /** The key variant {@code index} is placed under, like {@code procedural/trees/oak#0}. */
    public String variantKey(int index) {
        return key() + ProceduralObjectCatalog.VARIANT_SEPARATOR + index;
    }

    /** Every variant, baked on first use. */
    public List<PlaceableObject> variants() {
        List<PlaceableObject> known = baked;
        if (known != null) {
            return known;
        }
        synchronized (this) {
            if (baked == null) {
                baked = List.copyOf(bake(newBuilder(), BakeProfile.NONE));
            }
            return baked;
        }
    }

    /** Bakes every variant again the way world generation does, without keeping them, and times each stage. */
    public BakeProfile profileBake() {
        BakeProfile profile = new BakeProfile();
        bake(newBuilder(), profile);
        return profile;
    }

    /** One of the variants, picked with {@code rng}; empty when none baked. */
    public Optional<PlaceableObject> variant(RNG rng) {
        List<PlaceableObject> all = variants();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(rng.i(all.size())));
    }

    /** The variant baked at {@code index}; empty when it baked no blocks or the definition asks for fewer. */
    public Optional<PlaceableObject> variant(int index) {
        String key = variantKey(index);
        return variants().stream().filter(variant -> variant.name().equals(key)).findFirst();
    }

    /** The widest, tallest and deepest any variant reaches, each measured on its own. */
    public ObjectSize largestSize() {
        int width = 0;
        int height = 0;
        int depth = 0;
        for (PlaceableObject variant : variants()) {
            width = Math.max(width, variant.width());
            height = Math.max(height, variant.height());
            depth = Math.max(depth, variant.depth());
        }
        return new ObjectSize(width, height, depth);
    }

    /** Bakes every variant; variant {@code i} is drawn from {@code seed + i * stride}. */
    private List<PlaceableObject> bake(ObjectBuilder blocks, BakeProfile profile) {
        List<PlaceableObject> all = new ArrayList<>();
        for (int i = 0; i < variantCount; i++) {
            profile.startVariant();
            ProceduralShape generated = generate(i, variantRng(i), blocks, profile);
            if (generated == null || generated.blocks().isEmpty()) {
                continue;
            }
            all.add(blocks.build(variantKey(i), generated.width(), generated.height(), generated.depth(), generated.blocks()));
            profile.lap(BakeProfile.Stage.BUILD);
        }
        return all;
    }

    protected final RNG variantRng(int index) {
        return new RNG(seed + (index * VARIANT_SEED_STRIDE));
    }

    /** The index {@code variant} was baked at; -1 when it is not one of these variants. */
    protected final int bakeIndexOf(PlaceableObject variant) {
        for (int index = 0; index < variantCount; index++) {
            if (variantKey(index).equals(variant.name())) {
                return index;
            }
        }
        return -1;
    }

    protected final ObjectBuilder newBuilder() {
        return new ObjectBuilder(resources);
    }

    /** The seed every variant is baked from. */
    public final long seed() {
        return seed;
    }
}
