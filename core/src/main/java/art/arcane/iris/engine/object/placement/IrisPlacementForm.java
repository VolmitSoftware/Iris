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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.IrisValues;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.engine.object.noise.IrisNoiseGenerator;
import art.arcane.iris.engine.object.noise.IrisStyledRange;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.cache.LazyBoundedCache;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.Level;
import org.jetbrains.annotations.Nullable;

/**
 * What Iris makes of one object placement: the module settings its JSON declares, and its noise as Iris's own
 * generators, which keep the noise they build. Made once per placement and kept on it, so copies made with
 * {@link ObjectPlacement#toBuilder()} share it until their noise changes.
 */
public final class IrisPlacementForm {
    private static final int SURFACE_WARP_CACHE_SIZE = 8;
    private static final long SURFACE_WARP_SEED_OFFSET = 1024L;

    private final ModuleSettings moduleSettings;
    private final IrisGeneratorStyle warp;
    private final IrisStyledRange densityStyle;
    private final IrisNoiseGenerator heightmap;
    private final LazyBoundedCache<SurfaceWarpKey, CNG> surfaceWarps = new LazyBoundedCache<>(SURFACE_WARP_CACHE_SIZE);

    private IrisPlacementForm(ModuleSettings moduleSettings, IrisGeneratorStyle warp, IrisStyledRange densityStyle,
                              IrisNoiseGenerator heightmap) {
        this.moduleSettings = moduleSettings;
        this.warp = warp;
        this.densityStyle = densityStyle;
        this.heightmap = heightmap;
    }

    /** Iris's form of {@code placement}, made the first time it is asked for. */
    public static IrisPlacementForm of(ObjectPlacement placement, IrisData data) {
        if (placement.hostForm() instanceof IrisPlacementForm kept) {
            return kept;
        }
        IrisPlacementForm made = make(placement, data);
        placement.hostForm(made);
        return made;
    }

    private static IrisPlacementForm make(ObjectPlacement placement, IrisData data) {
        IrisPackResources resources = (IrisPackResources) data.getPackResources();
        return new IrisPlacementForm(moduleSettingsOf(placement, data),
                IrisValues.generatorStyle(placement.warp(), resources),
                placement.densityStyle().map(range -> IrisValues.styledRange(range, resources)).orElse(null),
                placement.heightmap().map(noise -> IrisValues.noiseGenerator(noise, resources)).orElse(null));
    }

    private static ModuleSettings moduleSettingsOf(ObjectPlacement placement, IrisData data) {
        if (placement.json() == null) {
            return ModuleSettings.NONE;
        }
        return data.getModules().settings().decode(Level.PLACEMENT, new JSONObject(placement.json()), "object placement");
    }

    /** The settings the pack's modules declare on the placement. */
    public ModuleSettings moduleSettings() {
        return moduleSettings;
    }

    public IrisGeneratorStyle warp() {
        return warp;
    }

    /** The virtual heightmap the objects place on, or null to place on the terrain. */
    public @Nullable IrisNoiseGenerator heightmap() {
        return heightmap;
    }

    /** How many objects to place at the given column: the fixed density, or the one the density style picks there. */
    public int density(ObjectPlacement placement, RNG rng, double x, double z, IrisData data) {
        if (densityStyle == null) {
            return placement.density();
        }
        return (int) Math.round(densityStyle.get(rng, x, z, data));
    }

    /** The noise that warps the surface the objects sit on, built once per world seed. */
    public CNG surfaceWarp(RNG rng, IrisData data, @Nullable Engine engine) {
        long seed = engine == null ? rng.getSeed() : engine.getSeedManager().getComponent() + SURFACE_WARP_SEED_OFFSET;
        return surfaceWarps.computeIfAbsent(new SurfaceWarpKey(data, engine, seed),
                ignored -> warp.create(new RNG(seed), data, engine));
    }

    private record SurfaceWarpKey(IrisData data, Engine engine, long seed) {
        @Override
        public boolean equals(Object other) {
            return other instanceof SurfaceWarpKey key && data == key.data && engine == key.engine && seed == key.seed;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(data);
            result = 31 * result + System.identityHashCode(engine);
            return 31 * result + Long.hashCode(seed);
        }
    }
}
