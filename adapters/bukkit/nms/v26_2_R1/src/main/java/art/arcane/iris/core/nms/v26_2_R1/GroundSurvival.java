package art.arcane.iris.core.nms.v26_2_R1;

import art.arcane.iris.spi.IrisLogging;
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

/** Asks the server's own rules whether a plant survives on a ground block, in a tiny level holding just the two. */
final class GroundSurvival {
    private static final BlockPos PLANT_POS = new BlockPos(0, 64, 0);
    private static final BlockPos GROUND_POS = PLANT_POS.below();
    private static final int MIN_BUILD_Y = -64;
    private static final int BUILD_HEIGHT = 384;
    // Crops need 8 or more and mushrooms less than 13. A chunk that is still generating has no light to ask for.
    private static final int LIGHT_EVERY_PLANT_ACCEPTS = 10;

    private final Map<BlockState, Boolean> restsOnGround = new ConcurrentHashMap<>();

    boolean canSurviveOn(BlockState plant, BlockState ground) {
        try {
            return survives(plant, ground) || !restsOnGround(plant);
        } catch (RuntimeException e) {
            IrisLogging.reportError(e);
            return true;
        }
    }

    void forgetRules() {
        restsOnGround.clear();
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

    // A proxy, since LevelReader gains abstract methods with every release and Paper's chunk system adds its own.
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
            case "getMinY" -> {
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
