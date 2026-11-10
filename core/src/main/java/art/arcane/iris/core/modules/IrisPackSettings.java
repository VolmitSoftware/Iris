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
import art.arcane.iris.util.common.data.DataProvider;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The settings an engine's pack declares, for working out mantle stage reaches when the pack loads and for vanilla
 * structure hooks: the engine's dimension, then every region it can reach, then every biome. Iris's built-in modules
 * decide from Iris's own pack data instead of module settings; they reach it through the engine the settings offer as an
 * {@linkplain #extension extension}.
 */
public final class IrisPackSettings implements PackSettings {
    static final String DIMENSION = "dimension";
    static final String REGION = "region";
    static final String BIOME = "biome";

    private final Engine engine;
    private final PackResourceIds ids;

    IrisPackSettings(Engine engine) {
        this.engine = engine;
        this.ids = new PackResourceIds(engine.getData().getDataFolder());
    }

    /** The engine whose pack is loading. */
    public Engine engine() {
        return engine;
    }

    @Override
    public PackResources resources() {
        return engine.getData().getPackResources();
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return HostExtensions.as(type, offered(type));
    }

    /** The engine is offered only for the built-ins that read Iris's own pack data. */
    private Object offered(Class<?> type) {
        return type == Engine.class ? engine : null;
    }

    @Override
    public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
        return declaredBy(engine.getDimension(), engine, ids, featureType);
    }

    /** The settings of {@code featureType} that {@code dimension} and every region and biome it reaches declare, in that order. */
    static <F extends IrisFeature> SettingsStack<F> declaredBy(IrisDimension dimension, DataProvider data, PackResourceIds ids,
                                                             Class<F> featureType) {
        List<Entry<F>> entries = new ArrayList<>();
        addEntry(entries, featureType, Level.DIMENSION, ids.of(DIMENSION, dimension.getLoadKey()), dimension);
        for (IrisRegion region : dimension.getAllRegions(data)) {
            addEntry(entries, featureType, Level.REGION, ids.of(REGION, region.getLoadKey()), region);
        }
        for (IrisBiome biome : dimension.getReachableBiomes(data)) {
            addEntry(entries, featureType, Level.BIOME, ids.of(BIOME, biome.getLoadKey()), biome);
        }
        return SettingsStack.of(entries);
    }

    /** Adds {@code owner}'s settings of {@code featureType}, if it declares them, as an entry from {@code source}. */
    static <F extends IrisFeature> void addEntry(List<Entry<F>> entries, Class<F> featureType, Level level, ResourceId source,
                                                 IrisRegistrant owner) {
        owner.getModuleSettings().find(featureType).ifPresent(value -> entries.add(new Entry<>(level, source, value)));
    }
}
