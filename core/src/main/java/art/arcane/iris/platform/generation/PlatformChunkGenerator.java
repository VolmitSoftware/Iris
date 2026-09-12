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

package art.arcane.iris.platform.generation;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineTarget;
import art.arcane.iris.generation.runtime.Hotloadable;
import art.arcane.iris.generation.block.DataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public interface PlatformChunkGenerator extends Hotloadable, DataProvider {
    @Nullable
    Engine getEngine();

    @Override
    default IrisData getData() {
        return getTarget().getData();
    }

    @NotNull
    EngineTarget getTarget();

    void close();

    default CompletableFuture<Void> closeAsync() {
        close();
        return CompletableFuture.completedFuture(null);
    }

    default CompletableFuture<Void> hotloadComplexAsync(long acquisitionTimeout, TimeUnit unit) {
        if (acquisitionTimeout <= 0L) {
            throw new IllegalArgumentException("Complex hotload acquisition timeout must be positive.");
        }
        Objects.requireNonNull(unit, "Complex hotload acquisition timeout unit");
        Engine activeEngine = getEngine();
        if (activeEngine == null) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            activeEngine.hotloadComplex();
            return CompletableFuture.completedFuture(null);
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    default void quiesceForServerShutdown() {
    }

    boolean isStudio();

    default boolean isClosing() {
        return false;
    }

    CompletableFuture<Integer> getSpawnChunks();

    default CompletableFuture<Void> getInitialSpawnReady() {
        return CompletableFuture.completedFuture(null);
    }
}
