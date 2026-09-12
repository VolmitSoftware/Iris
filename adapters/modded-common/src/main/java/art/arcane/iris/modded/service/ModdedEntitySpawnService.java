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

package art.arcane.iris.modded.service;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineWorldManager;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedServerLevels;
import art.arcane.iris.modded.ModdedWorldManager;
import art.arcane.iris.spi.IrisLogging;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class ModdedEntitySpawnService implements ModdedTickableService {
    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        for (ServerLevel level : ModdedServerLevels.levels(server)) {
            if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator)) {
                continue;
            }
            Engine engine = generator.engineIfBound();
            if (engine == null || engine.isClosed()) {
                continue;
            }
            EngineWorldManager worldManager = engine.getWorldManager();
            if (!(worldManager instanceof ModdedWorldManager moddedWorldManager)) {
                continue;
            }
            try {
                moddedWorldManager.serverTick(level);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }
    }
}
