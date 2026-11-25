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

import art.arcane.iris.generation.block.PlantPlacement;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Asks the game's own canSurvive rule whether a plant stands on a ground block, in a tiny level holding just the two. */
public final class ModdedGroundSurvival implements PlantPlacement.ServerRule {
    private static final BlockPos PLANT_POS = new BlockPos(0, 64, 0);
    private static final BlockPos GROUND_POS = PLANT_POS.below();
    private static final int MIN_BUILD_Y = -64;
    private static final int BUILD_HEIGHT = 384;
    // Crops need 8 or more and mushrooms less than 13. A chunk that is still generating has no light to ask for.
    private static final int LIGHT_EVERY_PLANT_ACCEPTS = 10;

    private final Map<BlockState, Map<BlockState, Boolean>> survivesOn = new ConcurrentHashMap<>();
    private final Map<BlockState, Boolean> restsOnGround = new ConcurrentHashMap<>();

    @Override
    public boolean canPlaceOnto(NativeBlockState plant, NativeBlockState ground) {
        return canSurviveOn((BlockState) plant.placementHandle(), (BlockState) ground.placementHandle());
    }

    public boolean canSurviveOn(BlockState plant, BlockState ground) {
        return survivesOn.computeIfAbsent(plant, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(ground, known -> decide(plant, known));
    }

    private boolean decide(BlockState plant, BlockState ground) {
        try {
            return survives(plant, ground) || !restsOnGround(plant);
        } catch (RuntimeException e) {
            IrisLogging.warn("Could not ask whether " + plant + " survives on " + ground + ", letting it stand: " + e);
            return true;
        }
    }

    // Wall and ceiling blocks fail on every ground, which says nothing about the ground they were decorated onto.
    private boolean restsOnGround(BlockState plant) {
        return restsOnGround.computeIfAbsent(plant, this::survivesOnSomeGround);
    }

    private boolean survivesOnSomeGround(BlockState plant) {
        for (Block block : BuiltInRegistries.BLOCK) {
            if (survives(plant, block.defaultBlockState())) {
                return true;
            }
        }
        return false;
    }

    private boolean survives(BlockState plant, BlockState ground) {
        return plant.canSurvive(levelAround(plant, ground), PLANT_POS);
    }

    // A proxy, since LevelReader gains abstract methods with every release and loaders add their own.
    private LevelReader levelAround(BlockState plant, BlockState ground) {
        InvocationHandler answers = (proxy, method, args) -> answer(proxy, method, args, plant, ground);
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[]{LevelReader.class}, answers);
    }

    private Object answer(Object proxy, Method method, Object[] args, BlockState plant, BlockState ground) throws Throwable {
        switch (method.getName()) {
            case "getBlockState", "getBlockStateIfLoaded" -> {
                return stateAt((BlockPos) args[0], plant, ground);
            }
            case "getFluidState", "getFluidIfLoaded" -> {
                return stateAt((BlockPos) args[0], plant, ground).getFluidState();
            }
            case "getRawBrightness", "getMaxLocalRawBrightness", "getBrightness" -> {
                return LIGHT_EVERY_PLANT_ACCEPTS;
            }
            case "getBlockEntity" -> {
                return null;
            }
            case "getMinY", "getMinBuildHeight" -> {
                return MIN_BUILD_Y;
            }
            case "getHeight" -> {
                if (args == null) {
                    return BUILD_HEIGHT;
                }
            }
            case "isClientSide" -> {
                return false;
            }
            case "hashCode" -> {
                return System.identityHashCode(proxy);
            }
            case "equals" -> {
                return proxy == args[0];
            }
            case "toString" -> {
                return "ground survival level";
            }
            default -> {
            }
        }

        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        throw new IllegalStateException("Ground survival level can't answer " + method.getName());
    }

    // Water beside the ground for sugar cane and air beside the plant for cactus, so only the ground can refuse.
    private BlockState stateAt(BlockPos pos, BlockState plant, BlockState ground) {
        if (pos.equals(PLANT_POS)) {
            return plant;
        }
        if (pos.equals(GROUND_POS)) {
            return ground;
        }
        return pos.getY() == GROUND_POS.getY() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }
}
