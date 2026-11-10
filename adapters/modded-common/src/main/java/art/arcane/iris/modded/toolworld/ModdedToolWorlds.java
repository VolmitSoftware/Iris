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

package art.arcane.iris.modded.toolworld;

import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.command.ModdedPackCommands;
import com.volmit.tooling.world.ToolWorld;
import com.volmit.tooling.world.ToolWorldDenied;
import com.volmit.tooling.world.ToolWorldSpec;
import com.volmit.tooling.world.ToolWorlds;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The tool worlds of one pack on a modded server. A tool world is made through the runtime dimensions the studio uses, with
 * the tool's painter installed before the level exists, so that even the first chunk is the tool's.
 */
final class ModdedToolWorlds implements ToolWorlds {
    static final String DIMENSION_NAMESPACE = "irisworldgen";
    private static final String DIMENSION_PREFIX = "tool_";
    private static final long TOOL_WORLD_SEED = 1337L;

    private final String packName;
    private final ToolPlayers players;

    ModdedToolWorlds(String packName, ToolPlayers players) {
        this.packName = packName;
        this.players = players;
    }

    @Override
    public CompletableFuture<ToolWorld> open(ToolWorldSpec spec) {
        MinecraftServer server = ModdedEngineBootstrap.currentServer();
        if (server == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No server is running"));
        }
        Optional<String> denial = packDenial();
        if (denial.isPresent()) {
            return CompletableFuture.failedFuture(new ToolWorldDenied(denial.get()));
        }
        String dimensionId = DIMENSION_NAMESPACE + ":" + DIMENSION_PREFIX + UUID.randomUUID().toString().replace("-", "");
        ModdedToolWorld world = new ModdedToolWorld(packName, spec.name(), dimensionId, spec.listener(),
                new ModdedToolPainting(spec.painter()), players);
        if (!ToolWorldRegistry.INSTANCE.register(world)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "A tool world called " + spec.name() + " is open already in pack " + packName));
        }
        CompletableFuture<ToolWorld> opened = new CompletableFuture<>();
        server.execute(() -> create(server, world, opened));
        return opened;
    }

    private void create(MinecraftServer server, ModdedToolWorld world, CompletableFuture<ToolWorld> opened) {
        try {
            ModdedDimensionManager.Handle handle = ModdedDimensionManager.create(
                    server, world.dimensionId(), packName, packName, TOOL_WORLD_SEED, world.painting());
            world.bind(handle.level());
            opened.complete(world);
        } catch (Throwable failure) {
            world.abandon();
            opened.completeExceptionally(failure);
        }
    }

    private Optional<String> packDenial() {
        File dimension = new File(new File(ModdedPackCommands.packsRoot(), packName), "dimensions/" + packName + ".json");
        if (!dimension.isFile()) {
            return Optional.of("Pack " + packName + " is not installed with dimensions/" + packName + ".json");
        }
        return Optional.empty();
    }

    @Override
    public Optional<ToolWorld> find(String name) {
        return ToolWorldRegistry.INSTANCE.forName(packName, name).filter(ModdedToolWorld::isBound).map(world -> world);
    }
}
