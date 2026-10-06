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
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.host.ConfiguredSettings;
import com.volmit.resource.ResourceId;

import java.util.List;

/**
 * Module settings at world positions, for one engine.
 * <p>
 * The settings that activate a chunk's stages come from its dimension, then the region and then the
 * biome at the chunk's center column, each type taking the most specific declaration: a biome's value
 * replaces its region's, a region's the dimension's. Iris resolves single-valued settings, such as a
 * cave profile, the same way. {@link #at} keeps the targets apart, as {@link FeatureSettings} promises.
 */
final class ChunkModuleSettings implements FeatureSettings {
    private final Engine engine;
    private final PackResourceIds ids;

    ChunkModuleSettings(Engine engine, PackResourceIds ids) {
        this.engine = engine;
        this.ids = ids;
    }

    static ConfiguredSettings configured(IrisDimension dimension, IrisRegion region, IrisBiome biome) {
        return settingsOf(dimension)
                .overriddenBy(settingsOf(region))
                .overriddenBy(settingsOf(biome))
                .configured();
    }

    @Override
    public <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, Target target, int worldX, int worldY, int worldZ) {
        return switch (target) {
            case DIMENSION -> entries(featureType, "dimension", engine.getDimension());
            case REGION -> entries(featureType, "region", engine.getRegion(worldX, worldZ));
            case BIOME -> entries(featureType, "biome", engine.getSurfaceBiome(worldX, worldZ));
            case OBJECT_PLACEMENT -> List.of();
        };
    }

    private <F extends IrisFeature> List<Entry<F>> entries(Class<F> featureType, String kind, IrisRegistrant owner) {
        if (owner == null) {
            return List.of();
        }
        ResourceId source = ids.of(kind, owner.getLoadKey());
        return owner.getModuleSettings().find(featureType)
                .map(value -> List.of(new Entry<>(source, source, value)))
                .orElse(List.of());
    }

    private static ModuleSettings settingsOf(IrisRegistrant owner) {
        return owner == null ? ModuleSettings.NONE : owner.getModuleSettings();
    }
}
