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

package art.arcane.iris.modded;

import art.arcane.iris.generation.decoration.DecoratorPlatformHooks;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ModdedDecoratorHooks implements DecoratorPlatformHooks.FaceFixer, DecoratorPlatformHooks.SurfaceSturdiness {
    private static final Direction[] CARTESIAN = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.UP, Direction.DOWN};
    private static final String[] FACE_NAMES = {"north", "east", "south", "west", "up", "down"};

    @Override
    public PlatformBlockState fixFaces(PlatformBlockState state, Hunk<PlatformBlockState> hunk, int rX, int rZ, int x, int y, int z, EngineMantle mantle) {
        if (!(state instanceof ModdedBlockState fabric)) {
            return state;
        }
        BlockState cloned = fabric.handle();
        Map<String, BooleanProperty> allowed = faceProperties(cloned);

        for (Map.Entry<String, BooleanProperty> entry : allowed.entrySet()) {
            if (cloned.getValue(entry.getValue())) {
                cloned = cloned.setValue(entry.getValue(), Boolean.FALSE);
            }
        }

        boolean found = false;
        for (Direction f : CARTESIAN) {
            int yy = y + f.getStepY();

            PlatformBlockState rs = null;
            if (mantle != null) {
                rs = mantle.getMantle().get(x + f.getStepX(), yy, z + f.getStepZ(), PlatformBlockState.class);
            }
            BlockState r = rs == null ? (BlockState) EngineMantle.AIR.get().nativeHandle() : (BlockState) rs.nativeHandle();
            if (isFaceSturdy(r, f.getOpposite())) {
                BooleanProperty property = allowed.get(f.getSerializedName());
                if (property != null) {
                    found = true;
                    cloned = cloned.setValue(property, Boolean.TRUE);
                }
                continue;
            }

            int xx = rX + f.getStepX();
            int zz = rZ + f.getStepZ();
            if (xx < 0 || xx > 15 || zz < 0 || zz > 15 || yy < 0 || yy > hunk.getHeight()) {
                continue;
            }

            r = (BlockState) hunk.get(xx, yy, zz).nativeHandle();
            if (isFaceSturdy(r, f.getOpposite())) {
                BooleanProperty property = allowed.get(f.getSerializedName());
                if (property != null) {
                    found = true;
                    cloned = cloned.setValue(property, Boolean.TRUE);
                }
            }
        }

        if (!found) {
            String fallback = allowed.containsKey("down") ? "down" : "up";
            BooleanProperty property = allowed.get(fallback);
            if (property != null) {
                cloned = cloned.setValue(property, Boolean.TRUE);
            }
        }

        return fabric.withHandle(cloned, fabric.parsedProperties());
    }

    @Override
    public boolean canGoOn(PlatformBlockState surface, boolean upward) {
        return isFaceSturdy((BlockState) surface.nativeHandle(), upward ? Direction.UP : Direction.DOWN);
    }

    private static boolean isFaceSturdy(BlockState state, Direction face) {
        return state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face, SupportType.FULL);
    }

    private static Map<String, BooleanProperty> faceProperties(BlockState state) {
        Map<String, BooleanProperty> properties = new LinkedHashMap<>();
        for (String name : FACE_NAMES) {
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(name) && property instanceof BooleanProperty bool) {
                    properties.put(name, bool);
                }
            }
        }
        return properties;
    }
}
