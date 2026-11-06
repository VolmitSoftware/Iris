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

package art.arcane.iris.engine.framework.structure.fit;

import com.volmit.geometry.BlockFace;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.resource.ResourceId;
import com.volmit.world.BlockAccess;

/**
 * The chunk's live blocks in world heights, and the block questions the structure fit asks. The fit's rules were
 * written in world heights (deepslate below 0, bands of world Y, noise sampled at world heights), so it keeps them and
 * converts here.
 */
final class FitBlocks {
    static final BlockState AIR = new BlockState("minecraft:air");
    static final BlockState STONE = new BlockState("minecraft:stone");
    static final BlockState COBBLESTONE = new BlockState("minecraft:cobblestone");
    private static final ResourceId STRUCTURE_VOID = ResourceId.parse("minecraft:structure_void");
    private static final ResourceId JIGSAW = ResourceId.parse("minecraft:jigsaw");
    private static final ResourceId LOGS_TAG = ResourceId.parse("minecraft:logs");
    private static final ResourceId LEAVES_TAG = ResourceId.parse("minecraft:leaves");

    private final BlockAccess access;
    private final int bottomY;

    FitBlocks(BlockAccess access, int bottomY) {
        this.access = access;
        this.bottomY = bottomY;
    }

    int bottomY() {
        return bottomY;
    }

    BlockState get(int x, int y, int z) {
        return access.get(x, y - bottomY, z);
    }

    void set(int x, int y, int z, BlockState state) {
        access.set(x, y - bottomY, z, state);
    }

    boolean isAir(BlockState state) {
        return access.is(state, BlockTrait.AIR);
    }

    boolean isSolid(BlockState state) {
        return access.is(state, BlockTrait.SOLID);
    }

    boolean holdsFluid(BlockState state) {
        return access.is(state, BlockTrait.FLUID);
    }

    /** Logs, wood, stems, hyphae and leaves: the blocks of trees the fit never treats as terrain. */
    boolean isTree(BlockState state) {
        return access.is(state, BlockTrait.LOG) || access.is(state, BlockTrait.LEAVES);
    }

    /** Logs or leaves by the platform's tags alone, as stilts tell vegetation from ground. */
    boolean isTaggedVegetation(BlockState state) {
        return access.inTag(state, LOGS_TAG) || access.inTag(state, LEAVES_TAG);
    }

    boolean inTag(BlockState state, ResourceId tag) {
        return access.inTag(state, tag);
    }

    /** Solid ground that is not part of a tree. */
    boolean isTerrain(BlockState state) {
        return isSolid(state) && !isTree(state);
    }

    /** Air or anything holding a fluid: the cells a fill may claim. */
    boolean isEncaseable(BlockState state) {
        return isAir(state) || holdsFluid(state);
    }

    /** A block a structure can stand on: solid, dry, and neither structure void nor a jigsaw. */
    boolean isSolidBase(BlockState state) {
        return state != null && isSolid(state)
                && !state.material().equals(STRUCTURE_VOID)
                && !state.material().equals(JIGSAW)
                && !holdsFluid(state);
    }

    boolean sturdyTop(int x, int y, int z) {
        return access.sturdy(x, y - bottomY, z, BlockFace.UP);
    }

    BlockState fluidOf(BlockState state) {
        return access.fluid(state);
    }
}
