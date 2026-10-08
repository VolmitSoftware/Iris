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

package art.arcane.iris.core.modules;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import com.volmit.block.BlockFace;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.resource.ResourceId;

/**
 * IrisApi's view of the bound platform's blocks. States convert through their keys, as {@link PlatformBlockStates}
 * does; the platform decides which blocks exist, so a key it does not know fails. Traits answer from what the
 * platform's block states can tell. Three traits ({@code GRAVITY}, {@code LOG}, {@code LEAVES}), tags and
 * attachment to a face are not offered by the platform layer yet, and asking for them throws
 * {@link UnsupportedOperationException} rather than guessing.
 */
final class IrisBlockRegistry implements BlockRegistry {
    @Override
    public BlockState resolve(String key) {
        return BlockState.parse(PlatformBlockStates.namespaced(platformState(key).key()));
    }

    @Override
    public boolean hasTrait(BlockState state, BlockTrait trait) {
        PlatformBlockState platform = platformState(state.key());
        return switch (trait) {
            case AIR -> platform.isAir();
            case SOLID -> platform.isSolid();
            case OCCLUDING -> platform.isOccluding();
            case FLUID -> platform.isFluid();
            case WATER -> platform.isWater();
            case FOLIAGE -> platform.isFoliage();
            case DECORATION -> platform.isDecorant();
            case ORE -> platform.isOre();
            case INVENTORY -> platform.isStorage();
            case BLOCK_ENTITY -> platform.hasTileEntity();
            case CUSTOM -> platform.isCustom();
            case NEEDS_UPDATE -> platform.isUpdatable();
            case GRAVITY, LOG, LEAVES -> throw unsupported("The " + trait + " trait");
        };
    }

    @Override
    public boolean inTag(BlockState state, ResourceId tag) {
        throw unsupported("Block tags");
    }

    @Override
    public boolean canAttach(BlockState state, BlockState support, BlockFace face) {
        throw unsupported("Attachment to a face");
    }

    private static PlatformBlockState platformState(String key) {
        PlatformBlockState state = IrisPlatforms.get().registries().blockOrNull(PlatformBlockStates.namespaced(key), false);
        if (state == null) {
            throw new IllegalArgumentException("Unknown block: " + key);
        }
        return state;
    }

    private static UnsupportedOperationException unsupported(String what) {
        return new UnsupportedOperationException(what + " cannot be answered yet: Iris's platform layer does not offer it");
    }
}
