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
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.common.data.DataProvider;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings an engine's pack declares, for working out mantle stage reaches when the pack loads and for vanilla
 * structure hooks: the engine's dimension, then every region it can reach, then every biome. Iris's built-in modules
 * decide from Iris's own pack data instead of module settings; they reach the engine through {@link #of(PackSettings)}.
 */
public final class IrisPackSettings implements PackSettings, ObjectHost.Source {
    static final String DIMENSION = "dimension";
    static final String REGION = "region";
    static final String BIOME = "biome";

    private final Engine engine;
    private final PackResourceIds ids;

    IrisPackSettings(Engine engine) {
        this.engine = engine;
        this.ids = new PackResourceIds(engine.getData().getDataFolder());
    }

    /** Iris's own view of {@code settings}, which Iris hands to every reach it works out. */
    public static IrisPackSettings of(PackSettings settings) {
        if (!(settings instanceof IrisPackSettings irisSettings)) {
            throw new IllegalArgumentException("Built-in modules read an Iris engine's pack, not " + settings);
        }
        return irisSettings;
    }

    /** The engine whose pack is loading. */
    public Engine engine() {
        return engine;
    }

    @Override
    public ObjectHost objects() {
        return new IrisObjectHost(engine, IrisApiLogger.hostLog());
    }

    @Override
    public <F extends IrisFeature> List<Entry<F>> declared(Class<F> featureType) {
        return declaredBy(engine.getDimension(), engine, ids, featureType);
    }

    /** The settings of {@code featureType} that {@code dimension} and every region and biome it reaches declare, in that order. */
    static <F extends IrisFeature> List<Entry<F>> declaredBy(IrisDimension dimension, DataProvider data, PackResourceIds ids,
                                                             Class<F> featureType) {
        List<Entry<F>> entries = new ArrayList<>();
        addEntry(entries, featureType, Target.DIMENSION, ids.of(DIMENSION, dimension.getLoadKey()), dimension);
        for (IrisRegion region : dimension.getAllRegions(data)) {
            addEntry(entries, featureType, Target.REGION, ids.of(REGION, region.getLoadKey()), region);
        }
        for (IrisBiome biome : dimension.getReachableBiomes(data)) {
            addEntry(entries, featureType, Target.BIOME, ids.of(BIOME, biome.getLoadKey()), biome);
        }
        return List.copyOf(entries);
    }

    /** Adds {@code owner}'s settings of {@code featureType}, if it declares them, as an entry from {@code source}. */
    static <F extends IrisFeature> void addEntry(List<Entry<F>> entries, Class<F> featureType, Target level, ResourceId source,
                                                 IrisRegistrant owner) {
        owner.getModuleSettings().find(featureType).ifPresent(value -> entries.add(new Entry<>(level, source, value)));
    }
}
