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

package art.arcane.iris.util.decree;

import art.arcane.volmlib.util.decree.DecreeExecutorBase;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import art.arcane.iris.util.plugin.VolmitSender;
import org.bukkit.entity.Player;

public interface DecreeExecutor extends DecreeExecutorBase {
    default VolmitSender sender() {
        return DecreeContext.get();
    }

    default Player player() {
        VolmitSender sender = sender();
        return sender == null ? null : sender.player();
    }

    default IrisData data() {
        var access = access();
        if (access != null) {
            return access.getData();
        }
        return null;
    }

    default Engine engine() {
        VolmitSender sender = sender();
        if (sender != null && sender.isPlayer() && IrisToolbelt.access(sender.player().getWorld()) != null) {
            PlatformChunkGenerator gen = IrisToolbelt.access(sender.player().getWorld());
            if (gen != null) {
                return gen.getEngine();
            }
        }

        return null;
    }

    default PlatformChunkGenerator access() {
        VolmitSender sender = sender();
        if (sender != null && sender.isPlayer()) {
            return IrisToolbelt.access(world());
        }
        return null;
    }
}
