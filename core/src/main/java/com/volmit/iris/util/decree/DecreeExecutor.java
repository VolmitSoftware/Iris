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

package com.volmit.iris.util.decree;

import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.World;
import org.bukkit.entity.Player;

public interface DecreeExecutor {
    default VolmitSender sender() {
        return DecreeContext.get();
    }

    default Player player() {
        return sender().player();
    }

    default Engine engine() {
        if (sender().isPlayer() && IrisToolbelt.access(sender().player().getWorld()) != null) {
            PlatformChunkGenerator gen = IrisToolbelt.access(sender().player().getWorld());
            if (gen != null) {
                return gen.getEngine();
            }
        }

        return null;
    }

    default PlatformChunkGenerator access() {
        if (sender().isPlayer()) {
            return IrisToolbelt.access(world());
        }
        return null;
    }

    default World world() {
        if (sender().isPlayer()) {
            return sender().player().getWorld();
        }
        return null;
    }

    default <T> T get(T v, T ifUndefined) {
        return v == null ? ifUndefined : v;
    }
}
