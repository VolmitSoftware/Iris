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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.lang.reflect.Method;

/** Where a plant can stand on Bukkit: the server's own canSurvive rule, or Iris's block table when the server can't be asked. */
public final class BukkitPlacementRules {
    private static final String CRAFT_BLOCK_DATA = "org.bukkit.craftbukkit.block.data.CraftBlockData";

    private BukkitPlacementRules() {
    }

    public static boolean canPlaceOnto(NativeBlockState plant, NativeBlockState ground) {
        ServerRules server = ServerConnection.RULES;
        if (server == null || !server.reaches(plant) || !server.reaches(ground)) {
            return plant.canPlaceOnto(ground);
        }
        return server.canPlaceOnto(plant, ground);
    }

    /** Drops the remembered answers, which a resource or custom block reload may have changed. */
    public static void forgetServerRules() {
        ServerRules server = ServerConnection.RULES;
        if (server != null) {
            server.survival().forgetRules();
        }
    }

    private record ServerRules(ServerGroundSurvival survival, Class<?> craftBlockData, Method serverState) {
        boolean reaches(NativeBlockState state) {
            return craftBlockData.isInstance(state.placementHandle());
        }

        // Custom blocks are asked as the server block they place, so their own placement rules apply.
        boolean canPlaceOnto(NativeBlockState plant, NativeBlockState ground) {
            try {
                return survival.canSurviveOn(serverState.invoke(plant.placementHandle()), serverState.invoke(ground.placementHandle()));
            } catch (ReflectiveOperationException e) {
                return plant.canPlaceOnto(ground);
            }
        }
    }

    private static final class ServerConnection {
        static final ServerRules RULES = open();

        private static ServerRules open() {
            ClassLoader loader = BukkitPlacementRules.class.getClassLoader();
            try {
                Class<?> craftBlockData = Class.forName(CRAFT_BLOCK_DATA, false, loader);
                return new ServerRules(ServerGroundSurvival.connect(loader), craftBlockData, craftBlockData.getMethod("getState"));
            } catch (ReflectiveOperationException | LinkageError e) {
                IrisLogging.debug("Placement uses Iris's block table, the server's rules are out of reach: " + e);
                return null;
            }
        }
    }
}
