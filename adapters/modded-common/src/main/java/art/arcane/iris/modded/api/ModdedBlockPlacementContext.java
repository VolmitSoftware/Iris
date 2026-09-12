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

import art.arcane.iris.generation.runtime.Engine;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.Objects;

/**
 * Everything a provider needs to finish a deferred block placement, handed to
 * {@link ModdedDataProvider#processBlockPlacement(ModdedBlockPlacementContext)} on the server thread.
 * <p>
 * Immutable, and constructed by Iris rather than by mods. {@code state} is defensively copied; {@code position} is
 * already immutable. Because delivery is on the server thread with the chunk loaded, it is safe to write blocks,
 * attach block entities and read neighbours from here.
 *
 * @param engine     the Iris engine for this level. Internal Iris type - treat it as an opaque token
 * @param level      the level to write into. Never null
 * @param position   the block the placeholder was written at. Never null
 * @param blockId    the identifier the pack named, without state properties. Never null
 * @param blockState the state currently at {@code position} - normally the placeholder returned as deferred, though
 *                   another provider or a later generation stage may have replaced it. Never null
 * @param state      the {@code [prop=value]} pairs from the pack's key, possibly empty. Never null; unmodifiable
 */
public record ModdedBlockPlacementContext(
        Engine engine,
        ServerLevel level,
        BlockPos position,
        Identifier blockId,
        Map<String, String> state,
        BlockState blockState) {
    /**
     * @throws NullPointerException if any component is null
     */
    public ModdedBlockPlacementContext {
        Objects.requireNonNull(engine);
        Objects.requireNonNull(level);
        Objects.requireNonNull(position);
        Objects.requireNonNull(blockId);
        state = Map.copyOf(state);
        Objects.requireNonNull(blockState);
    }
}
