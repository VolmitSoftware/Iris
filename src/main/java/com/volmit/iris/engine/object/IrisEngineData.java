/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import lombok.Data;

@Data
public class IrisEngineData {
    private IrisEngineStatistics statistics = new IrisEngineStatistics();
    private KList<IrisEngineSpawnerCooldown> spawnerCooldowns = new KList<>();
    private KList<IrisEngineChunkData> chunks = new KList<>();
    private Long seed = null;

    public void removeChunk(int x, int z) {
        long k = Cache.key(x, z);
        chunks.removeWhere((i) -> i.getChunk() == k);
    }

    public IrisEngineChunkData getChunk(int x, int z) {
        long k = Cache.key(x, z);

        for (IrisEngineChunkData i : chunks) {
            if (i.getChunk() == k) {
                return i;
            }
        }

        IrisEngineChunkData c = new IrisEngineChunkData();
        c.setChunk(k);
        chunks.add(c);
        return c;
    }

    public void cleanup(Engine engine) {
        for (IrisEngineSpawnerCooldown i : getSpawnerCooldowns().copy()) {
            IrisSpawner sp = engine.getData().getSpawnerLoader().load(i.getSpawner());

            if (sp == null || i.canSpawn(sp.getMaximumRate())) {
                getSpawnerCooldowns().remove(i);
            }
        }

        for (IrisEngineChunkData i : chunks.copy()) {
            i.cleanup(engine);

            if (i.isEmpty()) {
                getChunks().remove(i);
            }
        }
    }
}
