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
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

/**
 * A procedural object's settings made placeable: bakes a deterministic pool of variants from them, once, and turns
 * the placement options into the object placement the engine scatters. The settings classes held the same cache
 * and methods before they became module settings.
 */
public abstract class ResolvedProceduralPlacement implements IrisProceduralPlacement {
    private static final long VARIANT_SEED_STRIDE = 7919L;

    /** The options every procedural placement shares with an object placement. */
    public record Shape(String name, double chance, int density, long seed, int variants, ObjectPlaceMode mode,
                           ObjectRotation rotation, ObjectLimit clamp, CarvingMode carvingSupport, boolean underwater,
                           ObjectTranslate translate, StiltSettings stiltSettings, VacuumSettings vacuumSettings) {
    }

    private final Shape shape;
    private final AtomicCache<KList<IrisObject>> variantCache = new AtomicCache<>();

    protected ResolvedProceduralPlacement(Shape shape) {
        this.shape = shape;
    }

    /** The object variant {@code index} is baked as, from its own seeded stream. */
    protected abstract IrisObject generate(int index, RNG rng, IrisData data);

    /** The number of variants to bake; the settings' count, at least one. */
    protected int variantCount() {
        return Math.max(1, shape.variants());
    }

    @Override
    public String getName() {
        return shape.name();
    }

    @Override
    public double getChance() {
        return shape.chance();
    }

    @Override
    public int getDensity() {
        return shape.density();
    }

    @Override
    public boolean isPlausible() {
        return false;
    }

    @Override
    public KList<IrisObject> getVariantObjects(IrisData data) {
        return variantCache.aquire(() -> bake(data));
    }

    /** Bakes every variant; the default draws variant {@code i} from {@code seed + i * stride}. */
    protected KList<IrisObject> bake(IrisData data) {
        KList<IrisObject> baked = new KList<>();
        int count = variantCount();

        for (int i = 0; i < count; i++) {
            IrisObject object = generate(i, variantRng(i), data);
            if (object == null || object.getBlocks().isEmpty()) {
                continue;
            }
            object.setLoadKey("procedural/" + shape.name() + "#" + i);
            object.setLoader(data);
            baked.add(object);
        }

        return baked;
    }

    protected final RNG variantRng(int index) {
        return new RNG(shape.seed() + (index * VARIANT_SEED_STRIDE));
    }

    protected final long seed() {
        return shape.seed();
    }

    @Override
    public IrisObject getVariantObject(IrisData data, RNG rng) {
        KList<IrisObject> baked = getVariantObjects(data);
        if (baked == null || baked.isEmpty()) {
            return null;
        }
        return baked.get(rng.i(baked.size()));
    }

    @Override
    public IrisObjectPlacement asPlacement() {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setMode(shape.mode());
        placement.setRotation(irisRotation(shape.rotation()));
        placement.setClamp(shape.clamp());
        placement.setCarvingSupport(shape.carvingSupport());
        placement.setUnderwater(shape.underwater());
        placement.setTranslate(irisTranslate(shape.translate()));
        placement.setStiltSettings(shape.stiltSettings());
        placement.setVacuumSettings(shape.vacuumSettings());
        placement.setChance(shape.chance());
        placement.setDensity(shape.density());
        customize(placement);
        return placement;
    }

    /** Sets what this kind of placement adds to the common options. */
    protected void customize(IrisObjectPlacement placement) {
    }

    private static IrisObjectRotation irisRotation(ObjectRotation rotation) {
        return new IrisObjectRotation()
                .setEnabled(rotation.enabled())
                .setXAxis(rotation.xAxis())
                .setYAxis(rotation.yAxis())
                .setZAxis(rotation.zAxis());
    }

    private static IrisObjectTranslate irisTranslate(ObjectTranslate translate) {
        return new IrisObjectTranslate(translate.x(), translate.y(), translate.yRandom(), translate.z());
    }
}
