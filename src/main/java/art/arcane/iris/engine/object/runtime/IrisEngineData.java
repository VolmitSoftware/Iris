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

package art.arcane.iris.engine.object.runtime;

import art.arcane.iris.engine.object.entity.spawn.IrisSpawnerCooldowns;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.collection.KMap;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.function.ToLongFunction;

@Data
@EqualsAndHashCode(callSuper = true)
public class IrisEngineData extends IrisSpawnerCooldowns {
    private IrisEngineStatistics statistics = new IrisEngineStatistics();
    private KMap<Long, IrisSpawnerCooldowns> chunks = new KMap<>();
    private Long seed = null;

    public void removeChunk(int x, int z) {
        chunks.remove(CacheKey.key(x, z));
    }

    public IrisSpawnerCooldowns getChunk(int x, int z) {
        return chunks.computeIfAbsent(CacheKey.key(x, z), k -> new IrisSpawnerCooldowns());
    }

    @Override
    public void cleanup(ToLongFunction<String> intervalMilliseconds) {
        super.cleanup(intervalMilliseconds);

        chunks.values().removeIf(chunk -> {
            chunk.cleanup(intervalMilliseconds);
            return chunk.isEmpty();
        });
    }
}
