package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.builtin.structure.StructureSeeds;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

/**
 * What structure placement asks a world about its size, its seed and its terrain. Heights here are the world's own
 * (absolute) Y unless a method says it is above the dimension's bottom.
 */
public final class StructureWorld {
    private StructureWorld() {
    }

    public static RunningWorld running(PackWorld world) {
        return world.running().orElseThrow(() -> new IllegalStateException("Structure placement requires a running world"));
    }

    /** The key the pack's files name {@code id} by: its path in the pack's own namespace, the whole id in another. */
    public static String keyOf(PackWorld world, ResourceId id) {
        return id.namespace().equals(world.resources().packId().namespace()) ? id.path() : id.toString();
    }

    /** The seed placements start from. */
    public static long placementSeed(PackWorld world) {
        return world.seed(StructureSeeds.PLACEMENT);
    }

    /** How many blocks high the world is. */
    public static int height(PackWorld world) {
        var bounds = running(world).layout().bounds();
        return bounds.maxY() - bounds.minY();
    }

    /** The highest block of the column, with what objects and structures added, above the dimension's bottom. */
    public static int highestAboveBottom(PackWorld world, int blockX, int blockZ) {
        return running(world).highest(blockX, blockZ, true);
    }

    /** The highest block of the column in world Y. */
    public static int highest(PackWorld world, int blockX, int blockZ) {
        return highestAboveBottom(world, blockX, blockZ) + world.minWorldY();
    }

    /** The height up to which the dimension is filled with fluid, above the dimension's bottom. */
    public static int fluidHeightAboveBottom(PackWorld world) {
        return running(world).layout().fluidHeight();
    }
}
