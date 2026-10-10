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

package art.arcane.iris.core.content;

import art.arcane.iris.core.modules.ModuleServices;
import com.volmit.service.EntitySpawns;

import java.util.List;

/** What the modules plan to spawn, in host terms. */
public final class ContentSpawns {
    private ContentSpawns() {
    }

    /** The ground or fluid an entity spawns on. */
    public enum Surface {
        LAND, ANIMAL, WATER, OVERWORLD, LAVA
    }

    /**
     * One entity to spawn at a block, if the light there lies in the range (when {@link #restrictsLight()}) and, when
     * {@code checkSurface}, the block below suits the entity's surface and the space it needs is free.
     *
     * @param x            world X
     * @param worldY       world Y
     * @param z            world Z
     * @param checkSurface whether to check the surface and the free space
     */
    public record Attempt(int x, int worldY, int z, ContentEntity entity, Surface surface, int minLight, int maxLight,
                          boolean checkSurface) {
        /** Whether the light level matters. */
        public boolean restrictsLight() {
            return minLight > 0 || maxLight < 15;
        }
    }

    /** What a marker does: the keys of its spawners, whether it needs free space above it and whether it goes away when its block changes. */
    public record MarkerRule(List<String> spawners, boolean emptyAbove, boolean removeOnChange) {
    }

    /** Entities to spawn, tried one after the other; tell the plan how many appeared, so the spawner's rate limits count them. */
    public static final class Plan {
        private final ModuleServices.PlannedSpawn planned;
        private final List<Attempt> attempts;

        Plan(ModuleServices.PlannedSpawn planned, List<Attempt> attempts) {
            this.planned = planned;
            this.attempts = List.copyOf(attempts);
        }

        public List<Attempt> attempts() {
            return attempts;
        }

        /** Whether the marker that led here is used up and should be removed. */
        public boolean exhaustsMarker() {
            return planned.plan().exhaustsMarker();
        }

        /** Reports how many of the planned entities appeared. */
        public void spawned(int count) {
            planned.spawned(count);
        }
    }

    static Surface surfaceOf(EntitySpawns.Surface surface) {
        return Surface.valueOf(surface.name());
    }
}
