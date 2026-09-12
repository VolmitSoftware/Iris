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


import java.util.Objects;

/**
 * Immutable snapshot of everything an {@link IrisEngine} publishes as a single unit.
 * Instances are built by {@link EngineRuntimeBuilder} and retired by {@link EngineShutdownSequence}.
 */
record EngineRuntime(
        GenerationRuntime generation,
        EngineEffects effects,
        EngineWorldManager worldManager
) {
    EngineRuntime {
        Objects.requireNonNull(generation, "generation runtime");
        Objects.requireNonNull(effects, "engine effects");
        Objects.requireNonNull(worldManager, "engine world manager");
    }

    EngineRuntime withGeneration(GenerationRuntime nextGeneration) {
        return new EngineRuntime(nextGeneration, effects, worldManager);
    }
}
