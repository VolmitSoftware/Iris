/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine;

import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.volmit.iris.engine.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineParallaxManager;
import com.volmit.iris.engine.object.IrisFeaturePositional;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.documentation.BlockCoordinates;
import lombok.Getter;
import org.bukkit.util.Consumer;

public class IrisEngineParallax implements EngineParallaxManager {
    @Getter
    private final Engine engine;

    @Getter
    private final int parallaxSize;

    private final ConcurrentLinkedHashMap<Long, KList<IrisFeaturePositional>> featureCache = new ConcurrentLinkedHashMap.Builder<Long, KList<IrisFeaturePositional>>()
            .initialCapacity(1024)
            .maximumWeightedCapacity(1024)
            .concurrencyLevel(32)
            .build();

    public IrisEngineParallax(Engine engine) {
        this.engine = engine;
        parallaxSize = computeParallaxSize();
    }

    @Override
    @BlockCoordinates
    public void forEachFeature(double x, double z, Consumer<IrisFeaturePositional> f) {
        if (!getEngine().getDimension().hasFeatures(getEngine())) {
            return;
        }

        for (IrisFeaturePositional ipf : forEachFeature(x, z)) {
            f.accept(ipf);
        }
    }

    @Override
    @BlockCoordinates
    public KList<IrisFeaturePositional> forEachFeature(double x, double z) {
        int cx = ((int) x) >> 4;
        int cz = ((int) x) >> 4;
        long key = Cache.key(cx, cz);

        return featureCache.compute(key, (k, v) -> {
            if (v != null) {
                return v;
            }

            KList<IrisFeaturePositional> pos = new KList<>();
            pos.addAll(EngineParallaxManager.super.forEachFeature(cx << 4, cz << 4));
            pos.addAll(EngineParallaxManager.super.forEachFeature((cx << 4) + 15, cz << 4));
            pos.addAll(EngineParallaxManager.super.forEachFeature(cx << 4, (cz << 4) + 15));
            pos.addAll(EngineParallaxManager.super.forEachFeature((cx << 4) + 15, (cz << 4) + 15));
            pos.removeDuplicates();
            return pos;
        });
    }
}
