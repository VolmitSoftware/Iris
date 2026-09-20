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

package art.arcane.iris.nativegen.v26_3_R1;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;

public final class NativeStructureLocateResults {
    private NativeStructureLocateResults() {
    }

    public static <T> Pair<BlockPos, T> nearest(BlockPos origin, Pair<BlockPos, T> replacement,
                                                Pair<BlockPos, T> nativeResult) {
        if (replacement == null) {
            return nativeResult;
        }
        if (nativeResult == null) {
            return replacement;
        }
        return horizontalDistanceSquared(origin, nativeResult.getFirst())
                <= horizontalDistanceSquared(origin, replacement.getFirst()) ? nativeResult : replacement;
    }

    public static <T> Pair<BlockPos, T> selectAndReference(
            BlockPos origin,
            Pair<BlockPos, T> replacement, Runnable replacementReference,
            Pair<BlockPos, T> nativeResult, Runnable nativeReference) {
        Pair<BlockPos, T> selected = nearest(origin, replacement, nativeResult);
        if (selected == replacement && replacement != null) {
            replacementReference.run();
        } else if (selected == nativeResult && nativeResult != null) {
            nativeReference.run();
        }
        return selected;
    }

    private static long horizontalDistanceSquared(BlockPos origin, BlockPos target) {
        long dx = (long) target.getX() - origin.getX();
        long dz = (long) target.getZ() - origin.getZ();
        return dx * dx + dz * dz;
    }
}
