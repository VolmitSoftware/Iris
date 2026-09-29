/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.chunk.ColumnExtentListeningHunk;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

/**
 * Forwards a chunk's block writes to {@link BlockUpdater#catchBlockUpdates} at world coordinates.
 * The last state seen to need neither a block update nor custom handling is remembered, so the
 * long runs of plain terrain skip the per-block checks. Racing writers only ever store such states.
 */
final class ChunkBlockUpdateListener implements ColumnExtentListeningHunk.Listener<NativeBlockState> {
    private final BlockUpdater updater;
    private final int originX;
    private final int originZ;
    private NativeBlockState quiet;

    ChunkBlockUpdateListener(BlockUpdater updater, int originX, int originZ) {
        this.updater = updater;
        this.originX = originX;
        this.originZ = originZ;
    }

    @Override
    public void onWrite(int x, int y, int z, NativeBlockState state) {
        if (state == null || state == quiet) {
            return;
        }
        if (B.isUpdatable(state) || state.isCustom()) {
            updater.catchBlockUpdates(originX + x, y, originZ + z, state);
            return;
        }
        quiet = state;
    }
}
