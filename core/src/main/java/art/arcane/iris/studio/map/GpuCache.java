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

package art.arcane.iris.studio.map;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * What was put on the graphics card, by what it was made from. The sources are held weakly: once nothing else keeps
 * one, its upload can never be asked for again and is deleted. Only for the GPU thread.
 */
final class GpuCache<U extends GpuCache.Upload> {
    private final LinkedHashMap<Source, U> leastRecentlyUsedFirst = new LinkedHashMap<>(16, 0.75F, true);
    private final ReferenceQueue<Object> collectedSources = new ReferenceQueue<>();

    U get(Object source) {
        return leastRecentlyUsedFirst.get(Source.lookingFor(source));
    }

    void put(Object source, U upload) {
        leastRecentlyUsedFirst.put(new Source(source, collectedSources), upload);
    }

    void forgetCollected() {
        for (Reference<?> collected = collectedSources.poll(); collected != null; collected = collectedSources.poll()) {
            U upload = leastRecentlyUsedFirst.remove(collected);
            if (upload != null) {
                upload.delete();
            }
        }
    }

    /**
     * Deletes the least recently used uploads until the cache fits, sparing those made from what is kept.
     */
    void forgetBeyond(long mostBytes, Set<Object> kept) {
        long bytes = 0L;
        for (U upload : leastRecentlyUsedFirst.values()) {
            bytes += upload.bytes();
        }
        Iterator<Map.Entry<Source, U>> oldestFirst = leastRecentlyUsedFirst.entrySet().iterator();
        while (bytes > mostBytes && oldestFirst.hasNext()) {
            Map.Entry<Source, U> entry = oldestFirst.next();
            if (!kept.contains(entry.getKey().get())) {
                bytes -= entry.getValue().bytes();
                entry.getValue().delete();
                oldestFirst.remove();
            }
        }
    }

    interface Upload {
        long bytes();

        void delete();
    }

    // Equal only to a source of the same object, so a collected one is equal to nothing but itself.
    private static final class Source extends WeakReference<Object> {
        private final int identityHash;

        private Source(Object source, ReferenceQueue<Object> collected) {
            super(source, collected);
            identityHash = System.identityHashCode(source);
        }

        static Source lookingFor(Object source) {
            return new Source(source, null);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            Object source = get();
            return source != null && other instanceof Source otherSource && source == otherSource.get();
        }
    }
}
