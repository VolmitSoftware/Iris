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

package art.arcane.iris.core.toolworld;

import art.arcane.iris.core.runtime.WorldRuntimeControlService;
import org.bukkit.GameRules;
import org.bukkit.World;

/**
 * What every tool world is made to be, whatever the server's studio settings say: no natural mobs, no weather, no
 * daylight cycle, no portals, on top of the rules the object and jigsaw studios already use.
 */
final class ToolWorldRules {
    private ToolWorldRules() {
    }

    /** Applies the rules; runs on the thread that may change the world's rules (the main or global thread). */
    static void apply(World world) {
        WorldRuntimeControlService.get().applyObjectStudioWorldRules(world);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
        world.setStorm(false);
        world.setThundering(false);
    }
}
