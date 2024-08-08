/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.service;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisEngineService;
import com.volmit.iris.util.collection.KList;
import lombok.Getter;

@Getter
public class EngineStatusSVC extends IrisEngineService {
    private static final KList<EngineStatusSVC> INSTANCES = new KList<>();

    public EngineStatusSVC(Engine engine) {
        super(engine);
    }

    public static int getEngineCount() {
        return Math.max(INSTANCES.size(), 1);
    }

    public static Status getStatus() {
        synchronized (INSTANCES) {
            long loadedChunks = 0;
            long tectonicPlates = 0;
            long activeTectonicPlates = 0;
            long queuedTectonicPlates = 0;
            long minTectonicUnloadDuration = Long.MAX_VALUE;
            long maxTectonicUnloadDuration = Long.MIN_VALUE;

            for (var service : INSTANCES) {
                var world = service.engine.getWorld();
                if (world.hasRealWorld())
                    loadedChunks += world.realWorld().getLoadedChunks().length;
                if (world.hasHeadless())
                    loadedChunks += world.headless().getLoadedChunks();

                tectonicPlates += service.engine.getMantle().getLoadedRegionCount();
                activeTectonicPlates += service.engine.getMantle().getNotQueuedLoadedRegions();
                queuedTectonicPlates += service.engine.getMantle().getToUnload();
                minTectonicUnloadDuration = Math.min(minTectonicUnloadDuration, (long) service.engine.getMantle().getTectonicDuration());
                maxTectonicUnloadDuration = Math.max(maxTectonicUnloadDuration, (long) service.engine.getMantle().getTectonicDuration());
            }
            return new Status(INSTANCES.size(), loadedChunks, MantleCleanerSVC.getTectonicLimit(), tectonicPlates, activeTectonicPlates, queuedTectonicPlates, minTectonicUnloadDuration, maxTectonicUnloadDuration);
        }
    }

    @Override
    public void onEnable(boolean hotload) {
        if (hotload) return;
        synchronized (INSTANCES) {
            INSTANCES.add(this);
        }
    }

    @Override
    public void onDisable(boolean hotload) {
        if (hotload) return;

        synchronized (INSTANCES) {
            INSTANCES.remove(this);
        }
    }

    public record Status(int engineCount, long loadedChunks, int tectonicLimit,
                         long tectonicPlates, long activeTectonicPlates,
                         long queuedTectonicPlates,
                         long minTectonicUnloadDuration,
                         long maxTectonicUnloadDuration) {
    }
}
