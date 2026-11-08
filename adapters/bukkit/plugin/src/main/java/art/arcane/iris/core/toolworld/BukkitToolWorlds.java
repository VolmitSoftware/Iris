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

import art.arcane.iris.core.runtime.StudioOpenCoordinator;
import art.arcane.iris.core.runtime.StudioPainterSlots;
import art.arcane.iris.core.service.StudioSVC;
import art.arcane.iris.spi.IrisServices;
import com.volmit.tooling.world.ToolWorld;
import com.volmit.tooling.world.ToolWorldSpec;
import com.volmit.tooling.world.ToolWorlds;
import org.bukkit.World;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * The tool worlds of one pack on Bukkit. A tool world is created through the studio's own world creation, with the
 * tool's painter installed first so that even the first chunk is the tool's.
 */
final class BukkitToolWorlds implements ToolWorlds {
    private static final String STORAGE_NAME_PREFIX = "iris-";
    private static final long TOOL_WORLD_SEED = 1337L;

    private final String packName;
    private final OpenToolWorlds registry;
    private final ToolPlayers players;

    BukkitToolWorlds(String packName, OpenToolWorlds registry, ToolPlayers players) {
        this.packName = packName;
        this.registry = registry;
        this.players = players;
    }

    @Override
    public CompletableFuture<ToolWorld> open(ToolWorldSpec spec) {
        PendingBlockEntities pending = new PendingBlockEntities();
        StudioPainterSlots.Slot slot = new StudioPainterSlots.Slot(spec.painter(), pending::add);
        String storageName = STORAGE_NAME_PREFIX + UUID.randomUUID();
        BukkitToolWorld toolWorld = new BukkitToolWorld(packName, spec.name(), storageName, spec.listener(), slot, pending,
                registry, players);
        if (!registry.register(toolWorld)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "A tool world called " + spec.name() + " is open already in pack " + packName));
        }
        StudioPainterSlots.install(storageName, slot);
        CompletableFuture<ToolWorld> opened = new CompletableFuture<>();
        IrisServices.get(StudioSVC.class).occupyStudioSlot(
                toolWorld,
                () -> StudioOpenCoordinator.get().open(openRequest(storageName, toolWorld))
        ).whenComplete((result, failure) -> {
            if (failure != null) {
                toolWorld.abandon();
                opened.completeExceptionally(unwrap(failure));
                return;
            }
            opened.complete(toolWorld);
        });
        return opened;
    }

    @Override
    public Optional<ToolWorld> find(String name) {
        return registry.forName(packName, name).filter(BukkitToolWorld::isBound).map(world -> world);
    }

    private StudioOpenCoordinator.StudioOpenRequest openRequest(String storageName, BukkitToolWorld toolWorld) {
        return new StudioOpenCoordinator.StudioOpenRequest(
                packName,
                null,
                null,
                TOOL_WORLD_SEED,
                storageName,
                null,
                StudioOpenCoordinator.StudioOpenKind.TOOL,
                false,
                null,
                world -> prepare(toolWorld, world),
                System.nanoTime());
    }

    private static void prepare(BukkitToolWorld toolWorld, World created) {
        toolWorld.bind(created);
        ToolWorldRules.apply(created);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cursor = failure;
        while (cursor instanceof CompletionException && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor;
    }
}
