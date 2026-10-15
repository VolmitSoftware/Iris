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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Module settings at world columns, for one engine: the dimension's entry, then the entry of the column's
 * region, then the one of its surface biome. Each level that declares the settings is its own entry; levels
 * stack and are never merged, so a module sees all of them and decides what several mean.
 */
final class ChunkModuleSettings implements FeatureSettings {
    private static final String DIMENSION = "dimension";
    private static final String REGION = "region";
    private static final String BIOME = "biome";

    /** The registrants whose settings stack at a column. */
    interface Levels {
        IrisRegistrant dimension();

        IrisRegistrant region(int worldX, int worldZ);

        IrisRegistrant surfaceBiome(int worldX, int worldZ);

        /** The biome a {@code BiomeSample} names by {@code id}, or null when the dimension has no such biome. */
        IrisBiome biome(ResourceId id);
    }

    private final Levels levels;
    private final PackResourceIds ids;

    ChunkModuleSettings(Engine engine, PackResourceIds ids) {
        this(new EngineLevels(engine), ids);
    }

    ChunkModuleSettings(Levels levels, PackResourceIds ids) {
        this.levels = levels;
        this.ids = ids;
    }

    @Override
    public <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, int worldX, int worldZ) {
        List<Entry<F>> entries = new ArrayList<>(Target.values().length);
        addEntry(entries, featureType, Target.DIMENSION, DIMENSION, levels.dimension());
        addEntry(entries, featureType, Target.REGION, REGION, levels.region(worldX, worldZ));
        addEntry(entries, featureType, Target.BIOME, BIOME, levels.surfaceBiome(worldX, worldZ));
        return entries.isEmpty() ? List.of() : List.copyOf(entries);
    }

    @Override
    public <F extends IrisFeature> Optional<F> declaredBy(ResourceId biome, Class<F> featureType) {
        IrisBiome declared = levels.biome(biome);
        return declared == null ? Optional.empty() : declared.getModuleSettings().find(featureType);
    }

    private <F extends IrisFeature> void addEntry(List<Entry<F>> entries, Class<F> featureType, Target level, String kind,
                                                  IrisRegistrant owner) {
        if (owner == null) {
            return;
        }
        owner.getModuleSettings().find(featureType)
                .ifPresent(value -> entries.add(new Entry<>(level, ids.of(kind, owner.getLoadKey()), value)));
    }

    /** The dimension, region and surface biome the engine generates a column with. */
    private record EngineLevels(Engine engine) implements Levels {
        @Override
        public IrisRegistrant dimension() {
            return engine.getDimension();
        }

        @Override
        public IrisRegistrant region(int worldX, int worldZ) {
            return engine.getRegion(worldX, worldZ);
        }

        @Override
        public IrisRegistrant surfaceBiome(int worldX, int worldZ) {
            return engine.getSurfaceBiome(worldX, worldZ);
        }

        @Override
        public IrisBiome biome(ResourceId id) {
            return ((IrisPackResources) engine.getData().getPackResources()).irisBiome(id).orElse(null);
        }
    }
}
