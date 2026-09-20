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

package art.arcane.iris.modded.api;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;

import java.util.Objects;

/**
 * A provider's answer to a block lookup: the state to write, and whether the provider wants a second pass once the
 * chunk is loaded.
 * <p>
 * Immutable. Returned from {@link ModdedDataProvider#getBlockData(String, java.util.Map)};
 * construct with {@link #direct(ModdedBlockState)} or {@link #deferred(ModdedBlockState)} rather than the canonical constructor.
 *
 * @param state             the block state Iris writes. Never null
 * @param deferredPlacement whether {@link ModdedDataProvider#processBlockPlacement(ModdedBlockPlacementContext)}
 *                          should run for this position after the chunk is loaded
 */
public record ModdedBlockData(ModdedBlockState state, boolean deferredPlacement) {
    /**
     * @throws NullPointerException if {@code state} is null
     */
    public ModdedBlockData {
        Objects.requireNonNull(state);
    }

    /**
     * The state is final - Iris writes it during generation and does nothing further.
     */
    public static ModdedBlockData direct(ModdedBlockState state) {
        return new ModdedBlockData(state, false);
    }

    /**
     * {@code state} is a placeholder written during generation; the provider finishes the job in
     * {@link ModdedDataProvider#processBlockPlacement(ModdedBlockPlacementContext)} once the chunk is loaded. Use
     * when the real block needs a level - a block entity, neighbour state, or mod registries not available on a
     * generation thread. Pick a placeholder with the same shape and occlusion as the final block so terrain around
     * it generates correctly.
     */
    public static ModdedBlockData deferred(ModdedBlockState state) {
        return new ModdedBlockData(state, true);
    }
}
