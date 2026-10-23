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
import art.arcane.iris.engine.UpperDimensionContext;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.UpperDimension;

import java.util.Optional;

/** The dimension Iris stacks above the one it generates, as IrisApi's {@link UpperDimension}: its own dimension, region and biome streams. */
final class IrisUpperDimension implements UpperDimension {
    private final UpperDimensionContext upper;
    private final PackResources resources;
    private final PlatformBlockStates states;
    private final ChunkModuleSettings settings;

    IrisUpperDimension(Engine engine, UpperDimensionContext upper, PackResourceIds ids, PlatformBlockStates states) {
        this.upper = upper;
        this.resources = upper.getData().getPackResources();
        this.states = states;
        this.settings = new ChunkModuleSettings(new UpperLevels(upper, engine), ids);
    }

    @Override
    public boolean isThisDimension() {
        return upper.isSelfReferencing();
    }

    @Override
    public String key() {
        return upper.getDimension().getLoadKey();
    }

    @Override
    public FeatureSettings settings() {
        return settings;
    }

    @Override
    public PackResources resources() {
        return resources;
    }

    @Override
    public Optional<BlockState> surfaceBlock(int worldX, int worldZ) {
        return Optional.ofNullable(upper.getSurfaceBlock(worldX, worldZ)).map(states::toApi);
    }

    private record UpperLevels(UpperDimensionContext upper, Engine engine) implements ChunkModuleSettings.Levels {
        @Override
        public IrisRegistrant dimension() {
            return upper.getDimension();
        }

        @Override
        public IrisRegistrant region(int worldX, int worldZ) {
            return upper.getUpperRegion(worldX, worldZ);
        }

        @Override
        public IrisRegistrant surfaceBiome(int worldX, int worldZ) {
            return upper.getUpperBiome(worldX, worldZ);
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
            return (IrisPackResources) upper.getData().getPackResources();
        }
    }
}
