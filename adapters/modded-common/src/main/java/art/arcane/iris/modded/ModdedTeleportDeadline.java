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

package art.arcane.iris.modded;

import java.util.concurrent.TimeUnit;

/**
 * Deadline for a teleport that waits on its destination chunk. The first chunk of a cold dimension also loads the
 * pack and binds the engine; the deadline only bounds a destination whose chunk never completes (a refusing
 * generator or a removed dimension).
 */
public final class ModdedTeleportDeadline {
    private static final long DEADLINE_SECONDS = 120L;

    private ModdedTeleportDeadline() {
    }

    public static long fromNow() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(DEADLINE_SECONDS);
    }
}
