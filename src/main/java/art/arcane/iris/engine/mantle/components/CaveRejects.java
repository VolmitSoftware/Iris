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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import com.volmit.object.PlaceableObject;
import com.volmit.ModuleLog;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Debug lines for cave objects that were not placed, at most one per kind of rejection every few seconds. */
final class CaveRejects {
    private static final long THROTTLE_MILLIS = 5000L;
    private static final Map<String, State> STATES = new ConcurrentHashMap<>();

    private CaveRejects() {
    }

    static void log(ModuleLog log, String scope, String reason, int chunkX, int chunkZ, ObjectPlacement placement,
                    PlaceableObject object, int densityIndex, int density, CaveAnchors.Search search, Integer anchorY, Throwable error) {
        if (!log.debugging()) {
            return;
        }
        String placementKeys = placement == null ? "none" : String.join(",", placement.place());
        String objectKey = object == null ? "null" : object.name();
        String throttleKey = scope + "|" + reason + "|" + placementKeys + "|" + objectKey;
        State state = STATES.computeIfAbsent(throttleKey, key -> new State());
        long now = System.currentTimeMillis();
        long last = state.lastLogMillis.get();
        if ((now - last) < THROTTLE_MILLIS || !state.lastLogMillis.compareAndSet(last, now)) {
            state.suppressed.incrementAndGet();
            return;
        }
        int suppressed = state.suppressed.getAndSet(0);
        String errorText = error == null ? "none" : error.getClass().getSimpleName() + ":" + error.getMessage();
        log.debug("Cave object reject: scope=" + scope + " reason=" + reason + " chunk=" + chunkX + "," + chunkZ + " object=" + objectKey
                + " placements=" + placementKeys + " densityIndex=" + densityIndex + " density=" + density
                + " anchorMode=" + search.mode() + " anchorSearchAttempts=" + search.attempts() + " anchorScanStep=" + search.scanStep()
                + " minDepthBelowSurface=" + search.minDepthBelowSurface() + " anchorY=" + (anchorY == null ? "none" : anchorY)
                + " forcePlace=" + (placement != null && placement.forcePlace())
                + " carvingSupport=" + (placement == null ? "none" : placement.carvingSupport())
                + " suppressed=" + suppressed + " error=" + errorText);
    }

    private static final class State {
        private final AtomicLong lastLogMillis = new AtomicLong(0L);
        private final AtomicInteger suppressed = new AtomicInteger(0);
    }
}
