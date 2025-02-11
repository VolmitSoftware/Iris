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
import com.volmit.iris.util.collection.KMap;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IrisEngineData extends IrisSpawnerCooldowns {
    private IrisEngineStatistics statistics = new IrisEngineStatistics();
    private KMap<Long, IrisSpawnerCooldowns> chunks = new KMap<>();
    private Long seed = null;

    public void removeChunk(int x, int z) {
        chunks.remove(Cache.key(x, z));
    }

    public IrisSpawnerCooldowns getChunk(int x, int z) {
        return chunks.computeIfAbsent(Cache.key(x, z), k -> new IrisSpawnerCooldowns());
    }

    public void cleanup(Engine engine) {
        super.cleanup(engine);

        chunks.values().removeIf(chunk -> {
            chunk.cleanup(engine);
            return chunk.isEmpty();
        });
    }
}
