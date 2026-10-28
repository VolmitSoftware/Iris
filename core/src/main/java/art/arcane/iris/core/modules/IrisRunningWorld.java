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

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import com.volmit.config.FeatureSettings;
import com.volmit.geometry.BlockPosition;
import com.volmit.placement.PlacementRecord;
import com.volmit.resource.ResourceId;
import com.volmit.world.RunningWorld;
import com.volmit.world.TerrainSampler;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An engine as {@link RunningWorld} shows it to services and lifecycle hooks: its terrain streams over the whole world,
 * the settings at a column as chunk stages see them, and the engine's own answers about what is placed now.
 */
final class IrisRunningWorld implements RunningWorld {
    /** The farthest a Minecraft world reaches from its centre along X or Z, its largest world border. */
    private static final int WORLD_LIMIT_BLOCKS = 30_000_000;

    private final Engine engine;
    private volatile TerrainSampler layout;
    private volatile FeatureSettings settings;
    private volatile Set<ResourceId> platformLootTables;

    IrisRunningWorld(Engine engine) {
        this.engine = engine;
    }

    @Override
    public TerrainSampler layout() {
        TerrainSampler known = layout;
        if (known == null) {
            known = new IrisTerrainSampler(engine, packIds(), -WORLD_LIMIT_BLOCKS, -WORLD_LIMIT_BLOCKS, WORLD_LIMIT_BLOCKS, WORLD_LIMIT_BLOCKS);
            layout = known;
        }
        return known;
    }

    @Override
    public FeatureSettings settings() {
        FeatureSettings known = settings;
        if (known == null) {
            known = new ChunkModuleSettings(engine, packIds());
            settings = known;
        }
        return known;
    }

    private PackResourceIds packIds() {
        return new PackResourceIds(engine.getData().getDataFolder());
    }

    @Override
    public int highestBlock(int worldX, int worldZ, boolean ignoreFluid) {
        return engine.getHeight(worldX, worldZ, ignoreFluid);
    }

    @Override
    public List<BlockPosition> caveFloors(int chunkX, int chunkZ) {
        if (J.isFolia()) {
            return List.of();
        }
        int bottom = engine.getMinHeight();
        return engine.getMantle().findMarkers(chunkX, chunkZ, MarkerMatter.CAVE_FLOOR).stream()
                .map(floor -> new BlockPosition(floor.getX(), floor.getY() - bottom, floor.getZ()))
                .toList();
    }

    @Override
    public Optional<Sky> sky() {
        PlatformWorld world = engine.getWorld().platformWorld();
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Sky(world.getTime(), world.isStorming(), world.isThundering()));
    }

    @Override
    public Set<ResourceId> platformLootTables() {
        Set<ResourceId> known = platformLootTables;
        if (known == null) {
            known = IrisPlatforms.get().registries().lootTableKeys().stream().map(ResourceId::parse).collect(Collectors.toUnmodifiableSet());
            platformLootTables = known;
        }
        return known;
    }

    @Override
    public Optional<PlacementRecord> placementAt(int worldX, int y, int worldZ) {
        MantleChunk<Matter> chunk = engine.getMantle().getMantle().getChunk(worldX >> 4, worldZ >> 4).use();
        try {
            String recorded = chunk.get(worldX & 15, y, worldZ & 15, String.class);
            return PlacementMarkers.recordOf(recorded, engine.getData().getPackResources());
        } finally {
            chunk.release();
        }
    }

    @Override
    public boolean debug() {
        return engine.isStudio();
    }
}
