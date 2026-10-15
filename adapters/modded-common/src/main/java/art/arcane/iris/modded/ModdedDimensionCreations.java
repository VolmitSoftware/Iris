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

package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.command.ModdedPackCommands;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedDimensionCreations {
    private static final Map<String, ModdedDimensionInjection.Async<?, ?, ?>> ACTIVE = new ConcurrentHashMap<>();

    private ModdedDimensionCreations() {
    }

    public static CompletableFuture<Void> create(
            NativeModdedServer server,
            String dimensionId,
            String pack,
            String packDimensionKey,
            long seed
    ) {
        if (ModdedDimensionRegistryStore.get(server, dimensionId) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    ModdedDimensionManager.alreadyExists(dimensionId)));
        }
        ModdedDimensionRegistryStore.PersistentDimension dimension =
                new ModdedDimensionRegistryStore.PersistentDimension(dimensionId, pack, packDimensionKey, seed);
        ModdedDimensionInjection.Async<ModdedWorldEngines.PreparedPack, ModdedDimensionManager.PendingInjection, Engine> creation =
                ModdedDimensionInjection.async(dimension, new CreationSteps(server), server::execute);
        if (ACTIVE.putIfAbsent(dimensionId, creation) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException(beingCreated(dimensionId)));
        }
        CompletableFuture<Void> created = creation.result().handle(
                (ModdedDimensionInjection.Result result, Throwable failure) -> settle(creation, dimension, result));
        creation.start();
        return created;
    }

    public static boolean isCreating(String dimensionId) {
        return ACTIVE.containsKey(dimensionId);
    }

    public static void requireNotCreating(String dimensionId) {
        if (isCreating(dimensionId)) {
            throw new IllegalStateException(beingCreated(dimensionId));
        }
    }

    public static void cancelAll() {
        List<ModdedDimensionInjection.Async<?, ?, ?>> active = new ArrayList<>(ACTIVE.values());
        for (ModdedDimensionInjection.Async<?, ?, ?> creation : active) {
            creation.cancel();
        }
    }

    private static Void settle(
            ModdedDimensionInjection.Async<?, ?, ?> creation,
            ModdedDimensionRegistryStore.PersistentDimension dimension,
            ModdedDimensionInjection.Result result
    ) {
        ACTIVE.remove(dimension.id(), creation);
        Throwable cause = result == null
                ? new IllegalStateException("Iris dimension creation for '" + dimension.id() + "' ended without a result")
                : result.failure();
        if (cause instanceof Error fatal) {
            throw fatal;
        }
        if (cause != null) {
            throw cause instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(cause);
        }
        ModdedIrisLog.info("Iris created runtime dimension '{}' (pack={} dim={}): prepared {}ms, engine {}ms",
                dimension.id(), dimension.pack(), dimension.dimension(), result.preparedMillis(), result.builtMillis());
        return null;
    }

    private static String beingCreated(String dimensionId) {
        return "Iris dimension '" + dimensionId + "' is still being created. Try again when it is live.";
    }

    private record CreationSteps(NativeModdedServer server)
            implements ModdedDimensionInjection.Steps<ModdedWorldEngines.PreparedPack, ModdedDimensionManager.PendingInjection, Engine> {
        @Override
        public ModdedWorldEngines.PreparedPack prepare(ModdedDimensionRegistryStore.PersistentDimension dimension) {
            ModdedStartup.requirePackForWorldCreation(dimension.pack());
            IrisData data = IrisData.get(new File(ModdedPackCommands.packsRoot(), dimension.pack()));
            if (data.getDimensionLoader().load(dimension.dimension()) == null) {
                throw new MissingPackDimensionException(dimension.pack(), dimension.dimension());
            }
            return ModdedWorldEngines.prepareCreated(server, dimension);
        }

        @Override
        public ModdedDimensionManager.PendingInjection begin(
                ModdedDimensionRegistryStore.PersistentDimension dimension,
                ModdedWorldEngines.PreparedPack prepared
        ) {
            return ModdedDimensionManager.beginCreation(server, dimension, prepared.active());
        }

        @Override
        public Engine build(ModdedDimensionManager.PendingInjection level, ModdedWorldEngines.PreparedPack prepared) {
            if (level.present()) {
                prepared.close();
                return null;
            }
            return ModdedWorldEngines.createPrepared(level.handle().level(), level.handle().seed(), prepared);
        }

        @Override
        public void complete(ModdedDimensionManager.PendingInjection level, Engine engine) {
            ModdedDimensionManager.completeCreation(server, level, engine);
        }

        @Override
        public void abandon(ModdedDimensionManager.PendingInjection level, Engine engine, Throwable failure) {
            ModdedDimensionManager.abandonCreation(server, level, engine, failure);
        }

        @Override
        public void discard(ModdedWorldEngines.PreparedPack prepared) {
            prepared.close();
        }
    }
}
