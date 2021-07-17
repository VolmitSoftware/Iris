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
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineParallaxManager;
import com.volmit.iris.engine.object.IrisFeaturePositional;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.scheduling.IrisLock;
import lombok.Getter;

public class IrisEngineParallax implements EngineParallaxManager {
    @Getter
    private final Engine engine;

    @Getter
    private final int parallaxSize;

    @Getter
    private final IrisLock featureLock = new IrisLock("Feature");

    @Getter
    private final ConcurrentLinkedHashMap<Long, KList<IrisFeaturePositional>> featureCache = new ConcurrentLinkedHashMap.Builder<Long, KList<IrisFeaturePositional>>()
            .initialCapacity(1024)
            .maximumWeightedCapacity(1024)
            .concurrencyLevel(32)
            .build();

    public IrisEngineParallax(Engine engine) {
        this.engine = engine;
        parallaxSize = computeParallaxSize();
    }
}
