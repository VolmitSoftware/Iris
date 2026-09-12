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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.world.history.GenerationKernelRegistry;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.terrain.IrisDimension;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

record GenerationRuntime(
        int cacheId,
        EngineTarget target,
        IrisData data,
        IrisDimension dimension,
        GenerationKernelRegistry.Version kernelVersion,
        GenerationKernelRegistry.RuntimeKernel runtimeKernel,
        SeedManager seedManager,
        TransitionGenerationPlan transitionPlan,
        IrisComplex complex,
        UpperDimensionContext upperContext,
        DimensionStackContext dimensionStackContext,
        EngineMode mode,
        EngineMantle mantle,
        Path mantleStorageDirectory,
        CompletableFuture<Long> hash32,
        BiomeMaxes biomeMaxes
) {
    GenerationRuntime {
        Objects.requireNonNull(target, "generation target");
        Objects.requireNonNull(data, "generation data");
        Objects.requireNonNull(dimension, "generation dimension");
        Objects.requireNonNull(kernelVersion, "generation kernel version");
        Objects.requireNonNull(runtimeKernel, "executable generation kernel");
        Objects.requireNonNull(seedManager, "generation seed manager");
        if (!kernelVersion.equals(runtimeKernel.version())) {
            throw new IllegalArgumentException("Generation kernel version does not match its executable runtime.");
        }
        Objects.requireNonNull(complex, "generation complex");
        Objects.requireNonNull(mode, "generation mode");
        Objects.requireNonNull(mantle, "generation mantle");
        mantleStorageDirectory = IrisEngineMantle.normalizeStorageDirectory(mantleStorageDirectory);
        Objects.requireNonNull(hash32, "generation pack hash");
        Objects.requireNonNull(biomeMaxes, "generation biome maximums");
        if (target.getData() != data || target.getDimension() != dimension) {
            throw new IllegalArgumentException("Generation target, data, and dimension must describe one runtime.");
        }
    }

    GenerationRuntime withComplex(
            int nextCacheId,
            IrisComplex nextComplex,
            UpperDimensionContext nextUpperContext,
            DimensionStackContext nextDimensionStackContext,
            BiomeMaxes nextBiomeMaxes
    ) {
        return new GenerationRuntime(
                nextCacheId,
                target,
                data,
                dimension,
                kernelVersion,
                runtimeKernel,
                seedManager,
                transitionPlan,
                nextComplex,
                nextUpperContext,
                nextDimensionStackContext,
                mode,
                mantle,
                mantleStorageDirectory,
                hash32,
                nextBiomeMaxes);
    }

    record BiomeMaxes(double objectDensity, double layerDensity, double decoratorDensity) {
    }
}
