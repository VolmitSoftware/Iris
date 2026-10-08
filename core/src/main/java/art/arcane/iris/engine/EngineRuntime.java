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

package art.arcane.iris.engine;

import art.arcane.iris.engine.framework.EngineEffects;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.framework.EngineWorldManager;

import java.util.concurrent.CompletableFuture;

/**
 * Immutable snapshot of everything an {@link IrisEngine} publishes as a single unit.
 * Instances are built by {@link EngineRuntimeBuilder} and retired by {@link EngineShutdownSequence}.
 */
record EngineRuntime(
        int cacheId,
        EngineTarget target,
        IrisComplex complex,
        UpperDimensionContext upperContext,
        EngineEffects effects,
        EngineMode mode,
        EngineWorldManager worldManager,
        CompletableFuture<Long> hash32,
        BiomeMaxes biomeMaxes
) {
    EngineRuntime withComplex(
            int nextCacheId,
            IrisComplex nextComplex,
            UpperDimensionContext nextUpperContext,
            BiomeMaxes nextBiomeMaxes
    ) {
        return new EngineRuntime(
                nextCacheId,
                target,
                nextComplex,
                nextUpperContext,
                effects,
                mode,
                worldManager,
                hash32,
                nextBiomeMaxes);
    }

    record BiomeMaxes(double objectDensity, double layerDensity, double decoratorDensity) {
    }
}
