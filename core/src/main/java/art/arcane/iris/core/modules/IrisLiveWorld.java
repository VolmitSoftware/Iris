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

import art.arcane.iris.builtin.LiveWorld;
import art.arcane.iris.builtin.spawn.SpawnCooldowns;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import com.volmit.IrisFeature;
import com.volmit.resource.ResourceId;
import com.volmit.geometry.BlockPosition;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * An engine as {@link LiveWorld} shows it to the built-in services: the engine's own answers for a block, turned into pack
 * resource ids and values.
 */
final class IrisLiveWorld implements LiveWorld {
    private final Engine engine;
    private final IrisPackResources resources;
    private final SpawnCooldowns cooldowns;
    private volatile Set<String> platformLootTables;

    IrisLiveWorld(Engine engine, IrisPackResources resources) {
        this.engine = engine;
        this.resources = resources;
        this.cooldowns = new EngineSpawnCooldowns(engine);
    }

    @Override
    public int bottomY() {
        return engine.getMinHeight();
    }

    @Override
    public ResourceId surfaceBiome(int x, int z) {
        return idOf(engine.getSurfaceBiome(x, z));
    }

    @Override
    public ResourceId caveBiome(int x, int y, int z) {
        IrisBiome cave = engine.getCaveBiome(x, y, z);
        return cave == null ? surfaceBiome(x, z) : idOf(cave);
    }

    @Override
    public BiomeKind surfaceKind(int x, int z) {
        InferredType type = engine.getSurfaceBiome(x, z).getInferredType();
        return BiomeKind.valueOf(type.name());
    }

    @Override
    public double terrainHeight(int x, int z) {
        return engine.getComplex().getHeightStream().get(x, z);
    }

    @Override
    public int highestBlock(int x, int z, boolean ignoreFluid) {
        return engine.getHeight(x, z, ignoreFluid);
    }

    @Override
    public <F extends IrisFeature> Optional<F> settings(Class<F> type, Level level, int x, int y, int z) {
        IrisRegistrant owner = switch (level) {
            case DIMENSION -> engine.getDimension();
            case REGION -> engine.getRegion(x, z);
            case SURFACE_BIOME -> engine.getSurfaceBiome(x, z);
            case BIOME -> engine.getBiome(x, y, z);
            case CAVE_BIOME -> engine.getCaveBiome(x, y, z);
        };
        return owner == null ? Optional.empty() : owner.getModuleSettings().find(type);
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
    public boolean studio() {
        return engine.isStudio();
    }

    @Override
    public Set<String> platformLootTables() {
        Set<String> known = platformLootTables;
        if (known == null) {
            known = Set.copyOf(IrisPlatforms.get().registries().lootTableKeys());
            platformLootTables = known;
        }
        return known;
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
    public Optional<ObjectLoots> objectLoots(int x, int y, int z) {
        PlacedObject placed = engine.getObjectPlacement(x, y, z);
        IrisObjectPlacement placement = placed == null ? null : placed.getPlacement();
        if (placement == null) {
            return Optional.empty();
        }
        return Optional.of(new ObjectLoots(placement.getLoot(), placement.getVanillaLoot(), placement.isOverrideGlobalLoot()));
    }

    @Override
    public SpawnCooldowns spawnCooldowns() {
        return cooldowns;
    }

    private ResourceId idOf(IrisBiome biome) {
        return resources.idOf(biome.getLoadKey());
    }
}
