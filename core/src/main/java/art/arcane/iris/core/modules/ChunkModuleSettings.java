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
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.generation.ChunkContext;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Module settings at world columns, for one engine: the dimension's entry, then the entry of the column's
 * region, then the one of its surface biome. Each level that declares the settings is its own entry; levels
 * stack and are never merged, so a module sees all of them and decides what several mean. The stacks are kept
 * per pack snapshot ({@link SettingsStacks}), so a column whose region and biome were seen before costs no allocation.
 */
final class ChunkModuleSettings implements FeatureSettings {
    private static final String DIMENSION = "dimension";
    private static final String REGION = "region";
    private static final String BIOME = "biome";

    /** The registrants whose settings stack at a column, and the snapshot that keeps their stacks. */
    interface Levels {
        IrisRegistrant dimension();

        IrisRegistrant region(int worldX, int worldZ);

        IrisRegistrant surfaceBiome(int worldX, int worldZ);

        /** The biome a {@code BiomeSample} names by {@code id}, or null when the dimension has no such biome. */
        IrisBiome biome(ResourceId id);

        /** The stacks of the snapshot the registrants belong to. */
        SettingsStacks stacks();
    }

    private final Levels levels;
    private final PackResourceIds ids;
    private SettingsStacks stacks;

    ChunkModuleSettings(Engine engine, PackResourceIds ids) {
        this(new EngineLevels(engine), ids);
    }

    ChunkModuleSettings(Levels levels, PackResourceIds ids) {
        this.levels = levels;
        this.ids = ids;
    }

    @Override
    public <F extends IrisFeature> SettingsStack<F> at(Class<F> featureType, int worldX, int worldZ) {
        IrisRegistrant dimension = levels.dimension();
        IrisRegistrant region = levels.region(worldX, worldZ);
        IrisRegistrant biome = levels.surfaceBiome(worldX, worldZ);
        ModuleSettings dimensionSettings = settingsOf(dimension);
        ModuleSettings regionSettings = settingsOf(region);
        ModuleSettings biomeSettings = settingsOf(biome);
        SettingsStacks snapshotStacks = snapshotStacks();
        SettingsStack<F> known = snapshotStacks.find(dimensionSettings, regionSettings, biomeSettings, featureType);
        if (known != null) {
            return known;
        }
        SettingsStack<F> stack = stackOf(featureType, dimension, region, biome);
        snapshotStacks.remember(dimensionSettings, regionSettings, biomeSettings, featureType, stack);
        return stack;
    }

    private SettingsStacks snapshotStacks() {
        if (stacks == null) {
            stacks = levels.stacks();
        }
        return stacks;
    }

    /** Answered from the distinct regions and surface biomes of the chunk's columns, each asked for its settings once. */
    @Override
    public <F extends IrisFeature> SettingsStack<F> declaredInChunk(Class<F> featureType, int chunkX, int chunkZ) {
        List<IrisRegistrant> regions = new ArrayList<>();
        List<IrisRegistrant> biomes = new ArrayList<>();
        int originX = chunkX * ChunkContext.CHUNK_WIDTH;
        int originZ = chunkZ * ChunkContext.CHUNK_WIDTH;
        for (int x = 0; x < ChunkContext.CHUNK_WIDTH; x++) {
            for (int z = 0; z < ChunkContext.CHUNK_WIDTH; z++) {
                addDistinct(regions, levels.region(originX + x, originZ + z));
                addDistinct(biomes, levels.surfaceBiome(originX + x, originZ + z));
            }
        }
        List<Entry<F>> entries = new ArrayList<>();
        Set<ResourceId> sources = new HashSet<>();
        addEntry(entries, sources, featureType, Level.DIMENSION, DIMENSION, levels.dimension());
        regions.forEach(region -> addEntry(entries, sources, featureType, Level.REGION, REGION, region));
        biomes.forEach(biome -> addEntry(entries, sources, featureType, Level.BIOME, BIOME, biome));
        return SettingsStack.of(entries);
    }

    @Override
    public <F extends IrisFeature> Optional<F> declaredBy(ResourceId biome, Class<F> featureType) {
        IrisBiome declared = levels.biome(biome);
        return declared == null ? Optional.empty() : declared.getModuleSettings().find(featureType);
    }

    private <F extends IrisFeature> SettingsStack<F> stackOf(Class<F> featureType, IrisRegistrant dimension, IrisRegistrant region,
                                                             IrisRegistrant biome) {
        List<Entry<F>> entries = new ArrayList<>(Level.values().length);
        Set<ResourceId> sources = new HashSet<>();
        addEntry(entries, sources, featureType, Level.DIMENSION, DIMENSION, dimension);
        addEntry(entries, sources, featureType, Level.REGION, REGION, region);
        addEntry(entries, sources, featureType, Level.BIOME, BIOME, biome);
        return entries.isEmpty() ? SettingsStack.empty() : SettingsStack.of(entries);
    }

    private <F extends IrisFeature> void addEntry(List<Entry<F>> entries, Set<ResourceId> sources, Class<F> featureType, Level level,
                                                  String kind, IrisRegistrant owner) {
        if (owner == null) {
            return;
        }
        Optional<F> declared = owner.getModuleSettings().find(featureType);
        if (declared.isEmpty()) {
            return;
        }
        ResourceId source = ids.of(kind, owner.getLoadKey());
        if (sources.add(source)) {
            entries.add(new Entry<>(level, source, declared.get()));
        }
    }

    private static ModuleSettings settingsOf(IrisRegistrant owner) {
        return owner == null ? ModuleSettings.NONE : owner.getModuleSettings();
    }

    private static void addDistinct(List<IrisRegistrant> owners, IrisRegistrant owner) {
        if (owner == null) {
            return;
        }
        for (IrisRegistrant known : owners) {
            if (known == owner) {
                return;
            }
        }
        owners.add(owner);
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
            return packResources().irisBiome(id).orElse(null);
        }

        @Override
        public SettingsStacks stacks() {
            return packResources().settingsStacks();
        }

        private IrisPackResources packResources() {
            return (IrisPackResources) engine.getData().getPackResources();
        }
    }
}
