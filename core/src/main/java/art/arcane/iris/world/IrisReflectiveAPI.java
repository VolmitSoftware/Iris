package art.arcane.iris.world;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

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
        BukkitBlockResolution.registerCustomBlockData(namespace, key, blockData);
    }

    public static void retainMantleData(String classname) {
        WorldMaintenance.retainMantleDataForSlice(classname);
    }

    // These delegate to IrisToolbelt so the caller's WORLD Y is rebased into mantle space
    // (0..worldHeight). Raw pass-through silently no-opped below Y=0 and read the wrong
    // cell everywhere else, diverging from IrisToolbelt and IrisModdedAPI semantics.
    public static void setMantleData(World world, int x, int y, int z, Object data) {
        IrisToolbelt.setMantleData(world, x, y, z, data);
    }

    public static void deleteMantleData(World world, int x, int y, int z, Class c) {
        IrisToolbelt.deleteMantleData(world, x, y, z, c);
    }

    public static Object getMantleData(World world, int x, int y, int z, Class c) {
        return IrisToolbelt.getMantleData(world, x, y, z, c);
    }
}
