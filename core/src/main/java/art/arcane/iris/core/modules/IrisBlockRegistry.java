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
import art.arcane.iris.spi.PlatformFace;
import com.volmit.block.BlockFace;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.resource.ResourceId;

/**
 * IrisApi's view of the bound platform's blocks. States convert through their keys, as {@link PlatformBlockStates}
 * does; the platform decides which blocks exist, so a key it does not know fails. Traits, tags and attachment to
 * a face are the platform's own answers: its block tags, its falling blocks and its face support.
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
            case GRAVITY -> platform.hasGravity();
            case LOG -> platform.isLog();
            case LEAVES -> platform.isLeaves();
        };
    }

    @Override
    public boolean inTag(BlockState state, ResourceId tag) {
        return IrisPlatforms.get().registries().isInTag(platformState(state.key()), tag.toString());
    }

    @Override
    public boolean canAttach(BlockState state, BlockState support, BlockFace face) {
        return platformState(state.key()).canAttach(platformState(support.key()), PlatformFace.valueOf(face.name()));
    }

    private static PlatformBlockState platformState(String key) {
        PlatformBlockState state = IrisPlatforms.get().registries().blockOrNull(PlatformBlockStates.namespaced(key), false);
        if (state == null) {
            throw new IllegalArgumentException("Unknown block: " + key);
        }
        return state;
    }
}
