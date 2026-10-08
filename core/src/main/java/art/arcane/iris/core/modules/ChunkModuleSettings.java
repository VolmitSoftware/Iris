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
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Module settings at world columns, for one engine: the dimension's entry, then the entry of the column's
 * region, then the one of its surface biome. Each level that declares the settings is its own entry; levels
 * stack and are never merged, so a module sees all of them and decides what several mean.
 */
final class ChunkModuleSettings implements FeatureSettings {
    private static final String DIMENSION = "dimension";
    private static final String REGION = "region";
    private static final String BIOME = "biome";

    private final Engine engine;
    private final PackResourceIds ids;

    ChunkModuleSettings(Engine engine, PackResourceIds ids) {
        this.engine = engine;
        this.ids = ids;
    }

    @Override
    public <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, int worldX, int worldZ) {
        List<Entry<F>> entries = new ArrayList<>(Target.values().length);
        addEntry(entries, featureType, Target.DIMENSION, DIMENSION, engine.getDimension());
        addEntry(entries, featureType, Target.REGION, REGION, engine.getRegion(worldX, worldZ));
        addEntry(entries, featureType, Target.BIOME, BIOME, engine.getSurfaceBiome(worldX, worldZ));
        return entries.isEmpty() ? List.of() : List.copyOf(entries);
    }

    private <F extends IrisFeature> void addEntry(List<Entry<F>> entries, Class<F> featureType, Target level, String kind,
                                                  IrisRegistrant owner) {
        if (owner == null) {
            return;
        }
        owner.getModuleSettings().find(featureType)
                .ifPresent(value -> entries.add(new Entry<>(level, ids.of(kind, owner.getLoadKey()), value)));
    }
}
