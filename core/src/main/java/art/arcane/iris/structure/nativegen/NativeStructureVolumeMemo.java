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

package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EnginePlatformHooks;

import art.arcane.volmlib.util.collection.KList;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Chunk keyed memo in front of the platform volume hook. One chunk's object pass fires the same rect query dozens of
 * times, so the hook is asked once per chunk column and every later object reuses that answer. Long keyed so the
 * overwhelmingly common "no native structure anywhere near" lookup allocates nothing at all.
 */
public final class NativeStructureVolumeMemo {
    private static final int MAX_CACHED_CHUNKS = 2_048;

    private final Map<Integer, Long2ObjectLinkedOpenHashMap<KList<NativeStructureVolume>>> runtimes =
            new HashMap<>();

    public KList<NativeStructureVolume> volumes(Engine engine, EnginePlatformHooks hooks,
                                                int minX, int minZ, int maxX, int maxZ) {
        if (hooks == null) {
            return NativeStructureVolume.NONE;
        }

        int fromChunkX = Math.min(minX, maxX) >> 4;
        int toChunkX = Math.max(minX, maxX) >> 4;
        int fromChunkZ = Math.min(minZ, maxZ) >> 4;
        int toChunkZ = Math.max(minZ, maxZ) >> 4;
        KList<NativeStructureVolume> matches = null;
        for (int chunkX = fromChunkX; chunkX <= toChunkX; chunkX++) {
            for (int chunkZ = fromChunkZ; chunkZ <= toChunkZ; chunkZ++) {
                for (NativeStructureVolume volume : chunkVolumes(engine, hooks, chunkX, chunkZ)) {
                    if (!volume.intersectsRect(minX, minZ, maxX, maxZ)) {
                        continue;
                    }
                    if (matches == null) {
                        matches = new KList<>();
                    }
                    if (!matches.contains(volume)) {
                        matches.add(volume);
                    }
                }
            }
        }
        return matches == null ? NativeStructureVolume.NONE : matches;
    }

    public void clear() {
        synchronized (runtimes) {
            runtimes.clear();
        }
    }

    public void evictRuntime(int runtimeId) {
        synchronized (runtimes) {
            runtimes.remove(runtimeId);
        }
    }

    private KList<NativeStructureVolume> chunkVolumes(Engine engine, EnginePlatformHooks hooks,
                                                      int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        int runtimeId = engine.getCacheID();
        synchronized (runtimes) {
            Long2ObjectLinkedOpenHashMap<KList<NativeStructureVolume>> chunks = runtimes.get(runtimeId);
            if (chunks == null) {
                chunks = new Long2ObjectLinkedOpenHashMap<>();
                runtimes.put(runtimeId, chunks);
            }
            KList<NativeStructureVolume> hit = chunks.getAndMoveToFirst(key);
            if (hit != null) {
                return hit;
            }
        }

        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        KList<NativeStructureVolume> resolved = hooks.nativeStructureVolumes(engine, minX, minZ, minX + 15, minZ + 15);
        if (resolved == null) {
            resolved = NativeStructureVolume.NONE;
        }
        synchronized (runtimes) {
            Long2ObjectLinkedOpenHashMap<KList<NativeStructureVolume>> chunks = runtimes.computeIfAbsent(
                    runtimeId,
                    ignored -> new Long2ObjectLinkedOpenHashMap<>());
            chunks.putAndMoveToFirst(key, resolved);
            while (chunks.size() > MAX_CACHED_CHUNKS) {
                chunks.removeLast();
            }
        }
        return resolved;
    }
}
