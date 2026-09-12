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

package art.arcane.iris.command;

import art.arcane.volmlib.util.director.DirectorExecutorBase;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Arrays;

public interface DirectorExecutor extends DirectorExecutorBase {
    default VolmitSender sender() {
        return DirectorContext.get();
    }

    default Player player() {
        VolmitSender sender = sender();
        return sender == null ? null : sender.player();
    }

    default boolean playerWorldGeneratesStructures() {
        Player activePlayer = player();
        return activePlayer != null && activePlayer.getWorld().canGenerateStructures();
    }

    default IrisData data() {
        return authoringData(engine());
    }

    static IrisData authoringData(Engine engine) {
        if (engine == null) {
            return null;
        }
        if (engine.isStudio()) {
            return IrisData.get(engine.getPackSource().toFile());
        }
        if (engine.getDimension() == null) {
            return null;
        }
        String dimensionKey = engine.getDimension().getLoadKey();
        IrisData matching = null;
        for (File pack : PackDirectoryResolver.listVisiblePackDirectories(IrisPlatforms.get().packsFolder())) {
            IrisData candidate = IrisData.get(pack);
            if (!Arrays.asList(candidate.getDimensionLoader().getPossibleKeys()).contains(dimensionKey)) {
                continue;
            }
            if (matching != null) {
                return null;
            }
            matching = candidate;
        }
        return matching;
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
