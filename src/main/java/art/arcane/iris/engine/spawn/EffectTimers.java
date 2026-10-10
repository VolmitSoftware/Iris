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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.builtin.spawn.AmbientEffect;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.ChronoLatch;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * When the effects of a world play: an effect plays at most once per interval, and then only with its chance. The timers
 * are the world's, shared by every player, like the effects themselves.
 */
final class EffectTimers {
    private final ConcurrentMap<AmbientEffect, ChronoLatch> latches = new ConcurrentHashMap<>();

    /** Whether {@code effect} plays now; asking starts its interval over when it has elapsed. */
    boolean shouldPlayNow(AmbientEffect effect) {
        ChronoLatch latch = latches.computeIfAbsent(effect, key -> new ChronoLatch(key.interval()));
        return latch.flip() && RNG.r.nextInt(effect.chance()) == 0;
    }
}
