package com.volmit.iris.core.tools;

import com.volmit.iris.util.data.B;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * This class is used by an external IrisLib for other plugins to interact with Iris. Do not change
 * existing methods or their parameters as it will break the library that uses these methods
 * feel free to add more methods so long as you also add the reflective methods to the library
 */
public class IrisReflectiveAPI {
    public static boolean isIrisWorld(World world) {
        return IrisToolbelt.isIrisWorld(world);
    }

    public static boolean isIrisStudioWorld(World world) {
        return IrisToolbelt.isIrisStudioWorld(world);
    }

    public static void registerCustomBlockData(String namespace, String key, BlockData blockData) {
        B.registerCustomBlockData(namespace, key, blockData);
    }

    public static void retainMantleData(String classname) {
        IrisToolbelt.retainMantleDataForSlice(classname);
    }

    public static void setMantleData(World world, int x, int y, int z, Object data) {
        IrisToolbelt.access(world).getEngine().getMantle().getMantle().set(x, y, z, data);
    }

    public static void deleteMantleData(World world, int x, int y, int z, Class c) {
        IrisToolbelt.access(world).getEngine().getMantle().getMantle().remove(x, y, z, c);
    }

    public static Object getMantleData(World world, int x, int y, int z, Class c) {
        return IrisToolbelt.access(world).getEngine().getMantle().getMantle().get(x, y, z, c);
    }
}
