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

import art.arcane.iris.core.tooling.ToolWorldsHost;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.modded.service.ModdedTickableService;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolWorlds;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tool worlds on a modded server: the platform side of {@code com.volmit.tooling.world}. It hands each pack its
 * {@link ToolWorlds} through {@link ToolWorldsHost}, looks at the players once a tick for what the tools hear about them, and
 * closes every tool world when it stops. What vanilla code paths tell a tool world arrives through the mixins, which reach
 * {@link ToolWorldGuard} and the static hooks here.
 */
public final class ModdedToolWorldService implements ModdedTickableService, ToolWorldsHost {
    private static final long CLOSING_WAIT_SECONDS = 30L;
    private static final ToolPlayers PLAYERS = new ToolPlayers();

    static ToolPlayers players() {
        return PLAYERS;
    }

    /** The tool player for {@code player}, the same object for as long as the player is online. */
    public static ToolPlayer toolPlayerOf(ServerPlayer player) {
        return PLAYERS.of(player);
    }

    @Override
    public ToolWorlds worldsOf(String packName) {
        return new ModdedToolWorlds(packName, PLAYERS);
    }

    @Override
    public void onEnable() {
        ToolWorldGuard.resetScopes();
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        ToolWorldGuard.resetScopes();
        PLAYERS.track(server);
    }

    @Override
    public void onDisable() {
        List<ModdedToolWorld> open = ToolWorldRegistry.INSTANCE.all();
        awaitClosing(open);
        MinecraftServer server = art.arcane.iris.modded.ModdedEngineBootstrap.currentServer();
        open.forEach(world -> world.shutDownNow(server));
        PLAYERS.clear();
        ToolWorldRegistry.INSTANCE.clear();
    }

    /** Lets every tool hear that its world is closing, as a replacement does, so it can save what it holds before the world is gone. */
    private static void awaitClosing(List<ModdedToolWorld> open) {
        CompletableFuture<?>[] closing = open.stream()
                .map(world -> world.prepareReplacement(Optional.empty()))
                .toArray(CompletableFuture[]::new);
        try {
            CompletableFuture.allOf(closing).get(CLOSING_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException notReady) {
            ModdedIrisLog.error("A tool world was not ready to close when Iris stopped.", notReady);
        }
    }

    /** Why a studio change must not close the tool worlds now; the first refusal of a tool wins. */
    public static Optional<String> studioChangeRefusal() {
        return ToolWorldRegistry.INSTANCE.all().stream()
                .map(ModdedToolWorld::closeRefusal)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** Closes every tool world for a studio that replaces them or is closed: each tool finishes its work first. */
    public static CompletableFuture<Void> closeForStudioChange(Optional<ServerPlayer> by) {
        Optional<ToolPlayer> requestedBy = by.map(PLAYERS::of);
        List<ModdedToolWorld> open = ToolWorldRegistry.INSTANCE.all();
        CompletableFuture<?>[] closing = open.stream()
                .map(world -> world.prepareReplacement(requestedBy))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(closing).thenCompose(ignored -> CompletableFuture.allOf(
                open.stream().map(ModdedToolWorld::close).toArray(CompletableFuture[]::new)));
    }

    /** Minecraft unloaded {@code level}: if it was a tool world, it is gone for the tool. */
    public static void levelUnloaded(ServerLevel level) {
        if (ToolWorldRegistry.INSTANCE.hasWorlds()) {
            ToolWorldRegistry.INSTANCE.forLevel(level).ifPresent(ModdedToolWorld::unloaded);
        }
    }

    /** A chunk of {@code level} is live: tells the tool when the level is a tool world. */
    public static void chunkLoaded(ServerLevel level, int chunkX, int chunkZ) {
        if (ToolWorldRegistry.INSTANCE.hasWorlds()) {
            ToolWorldRegistry.INSTANCE.forLevel(level).ifPresent(world -> world.tell(listener -> listener.chunkLoaded(chunkX, chunkZ)));
        }
    }
}
