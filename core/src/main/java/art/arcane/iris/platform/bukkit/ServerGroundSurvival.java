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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.spi.IrisLogging;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks the server's own canSurvive rule whether a plant stands on a ground block, in a tiny level holding just the
 * two. Iris ships no server-mapped code for Bukkit, so the server's classes are reached by their runtime names.
 */
public final class ServerGroundSurvival {
    private static final int PLANT_Y = 64;
    private static final int MIN_BUILD_Y = -64;
    private static final int BUILD_HEIGHT = 384;
    // Crops need 8 or more and mushrooms less than 13. A chunk that is still generating has no light to ask for.
    private static final int LIGHT_EVERY_PLANT_ACCEPTS = 10;

    private final ServerClasses server;
    private final Map<Object, Map<Object, Boolean>> survivesOn = new ConcurrentHashMap<>();
    private final Map<Object, Boolean> restsOnGround = new ConcurrentHashMap<>();

    private ServerGroundSurvival(ServerClasses server) {
        this.server = server;
    }

    public static ServerGroundSurvival connect(ClassLoader loader) throws ReflectiveOperationException {
        return new ServerGroundSurvival(ServerClasses.load(loader));
    }

    /** Whether the server lets the server block state {@code plant} stand on the server block state {@code ground}. */
    public boolean canSurviveOn(Object plant, Object ground) {
        return survivesOn.computeIfAbsent(plant, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(ground, known -> decide(plant, known));
    }

    public void forgetRules() {
        survivesOn.clear();
        restsOnGround.clear();
    }

    private boolean decide(Object plant, Object ground) {
        try {
            return survives(plant, ground) || !restsOnGround(plant);
        } catch (ReflectiveOperationException | RuntimeException e) {
            IrisLogging.warn("Could not ask whether " + plant + " survives on " + ground + ", letting it stand: " + rootCause(e));
            return true;
        }
    }

    // Wall and ceiling blocks fail on every ground, which says nothing about the ground they were decorated onto.
    private boolean restsOnGround(Object plant) throws ReflectiveOperationException {
        Boolean known = restsOnGround.get(plant);
        if (known != null) {
            return known;
        }
        boolean rests = survivesOnSomeGround(plant);
        restsOnGround.put(plant, rests);
        return rests;
    }

    private boolean survivesOnSomeGround(Object plant) throws ReflectiveOperationException {
        for (Object block : server.blocks()) {
            if (survives(plant, server.defaultBlockState().invoke(block))) {
                return true;
            }
        }
        return false;
    }

    private boolean survives(Object plant, Object ground) throws ReflectiveOperationException {
        return (boolean) server.canSurvive().invoke(plant, levelAround(plant, ground), server.plantPos());
    }

    // A proxy, since LevelReader gains abstract methods with every release and Paper's chunk system adds its own.
    private Object levelAround(Object plant, Object ground) {
        InvocationHandler answers = (proxy, method, args) -> answer(proxy, method, args, plant, ground);
        return Proxy.newProxyInstance(server.levelReader().getClassLoader(), new Class<?>[]{server.levelReader()}, answers);
    }

    private Object answer(Object proxy, Method method, Object[] args, Object plant, Object ground) throws Throwable {
        switch (method.getName()) {
            case "getBlockState", "getBlockStateIfLoaded" -> {
                return stateAt(args[0], plant, ground);
            }
            case "getFluidState", "getFluidIfLoaded" -> {
                return server.getFluidState().invoke(stateAt(args[0], plant, ground));
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
    private Object stateAt(Object pos, Object plant, Object ground) throws ReflectiveOperationException {
        if (pos.equals(server.plantPos())) {
            return plant;
        }
        if (pos.equals(server.groundPos())) {
            return ground;
        }
        return (int) server.getY().invoke(pos) == PLANT_Y - 1 ? server.water() : server.air();
    }

    private static String rootCause(Throwable failure) {
        Throwable cause = failure instanceof InvocationTargetException invocation && invocation.getCause() != null
                ? invocation.getCause() : failure;
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private record ServerClasses(Class<?> levelReader, Method canSurvive, Method getFluidState, Method getY,
                                 Method defaultBlockState, Object plantPos, Object groundPos, Object water, Object air,
                                 Iterable<?> blocks) {
        static ServerClasses load(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> blockState = Class.forName("net.minecraft.world.level.block.state.BlockState", false, loader);
            Class<?> levelReader = Class.forName("net.minecraft.world.level.LevelReader", false, loader);
            Class<?> blockPos = Class.forName("net.minecraft.core.BlockPos", false, loader);
            Class<?> block = Class.forName("net.minecraft.world.level.block.Block", false, loader);
            Class<?> blocks = Class.forName("net.minecraft.world.level.block.Blocks", true, loader);
            Class<?> registries = Class.forName("net.minecraft.core.registries.BuiltInRegistries", true, loader);
            Method defaultBlockState = block.getMethod("defaultBlockState");
            Constructor<?> position = blockPos.getConstructor(int.class, int.class, int.class);
            return new ServerClasses(levelReader,
                    blockState.getMethod("canSurvive", levelReader, blockPos),
                    blockState.getMethod("getFluidState"),
                    blockPos.getMethod("getY"),
                    defaultBlockState,
                    position.newInstance(0, PLANT_Y, 0),
                    position.newInstance(0, PLANT_Y - 1, 0),
                    defaultBlockState.invoke(blocks.getField("WATER").get(null)),
                    defaultBlockState.invoke(blocks.getField("AIR").get(null)),
                    (Iterable<?>) registries.getField("BLOCK").get(null));
        }
    }
}
