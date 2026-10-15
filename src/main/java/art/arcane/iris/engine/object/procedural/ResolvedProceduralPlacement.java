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

package art.arcane.iris.engine.object.procedural;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.builtin.objects.ObjectBuilder;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A procedural object's settings made placeable: bakes a deterministic pool of variants from them, once, through the
 * host's object builder, and hands the placement options to {@code iris:objects}.
 */
public abstract class ResolvedProceduralPlacement implements ProceduralPlacement {
    private static final long VARIANT_SEED_STRIDE = 7919L;

    /** The options every procedural placement shares with an object placement. */
    public record Shape(String name, double chance, int density, long seed, int variants, ObjectPlaceMode mode,
                           ObjectRotation rotation, ObjectLimit clamp, CarvingMode carvingSupport, boolean underwater,
                           ObjectTranslate translate, StiltSettings stiltSettings, VacuumSettings vacuumSettings) {
    }

    private final Shape shape;
    private final ObjectHost host;
    private final AtomicCache<List<PlaceableObject>> variantCache = new AtomicCache<>();

    protected ResolvedProceduralPlacement(Shape shape, ObjectHost host) {
        this.shape = shape;
        this.host = host;
    }

    /** The shape variant {@code index} is baked as, from its own seeded stream; null when it has no blocks. */
    protected abstract ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks);

    /** The number of variants to bake; the settings' count, at least one. */
    protected int variantCount() {
        return Math.max(1, shape.variants());
    }

    @Override
    public String name() {
        return shape.name();
    }

    @Override
    public double chance() {
        return shape.chance();
    }

    @Override
    public int density() {
        return shape.density();
    }

    @Override
    public boolean plausible() {
        return false;
    }

    @Override
    public boolean tree() {
        return false;
    }

    @Override
    public ObjectPlaceMode mode() {
        return shape.mode();
    }

    @Override
    public CarvingMode carvingSupport() {
        return shape.carvingSupport();
    }

    @Override
    public CaveAnchorMode caveAnchorMode() {
        return CaveAnchorMode.PROFILE_DEFAULT;
    }

    @Override
    public boolean underwater() {
        return shape.underwater();
    }

    @Override
    public ObjectRotation rotation() {
        return shape.rotation();
    }

    @Override
    public ObjectTranslate translate() {
        return shape.translate();
    }

    @Override
    public Optional<VacuumSettings> vacuumSettings() {
        return Optional.ofNullable(shape.vacuumSettings());
    }

    @Override
    public ObjectLimit clamp() {
        return shape.clamp();
    }

    @Override
    public Optional<StiltSettings> stiltSettings() {
        return Optional.ofNullable(shape.stiltSettings());
    }

    @Override
    public List<PlaceableObject> variants() {
        return variantCache.aquire(() -> bake(host.builder()));
    }

    @Override
    public Optional<PlaceableObject> variant(RNG rng) {
        List<PlaceableObject> baked = variants();
        if (baked == null || baked.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(baked.get(rng.i(baked.size())));
    }

    /** Bakes every variant; the default draws variant {@code i} from {@code seed + i * stride}. */
    protected List<PlaceableObject> bake(ObjectBuilder blocks) {
        List<PlaceableObject> baked = new ArrayList<>();
        int count = variantCount();

        for (int i = 0; i < count; i++) {
            ProceduralShape generated = generate(i, variantRng(i), blocks);
            if (generated == null || generated.blocks().isEmpty()) {
                continue;
            }
            baked.add(build(blocks, "procedural/" + shape.name() + "#" + i, generated));
        }

        return baked;
    }

    protected static PlaceableObject build(ObjectBuilder blocks, String loadKey, ProceduralShape generated) {
        return blocks.build(loadKey, generated.width(), generated.height(), generated.depth(), generated.blocks());
    }

    protected final RNG variantRng(int index) {
        return new RNG(shape.seed() + (index * VARIANT_SEED_STRIDE));
    }

    protected final long seed() {
        return shape.seed();
    }
}
