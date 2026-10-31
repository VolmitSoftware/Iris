package art.arcane.iris.probe.fidelity;

import java.util.List;
import java.util.Set;

final class FidelityBlocks {
    static final String AIR = "minecraft:air";
    static final String LAVA = "minecraft:lava";

    private static final Set<String> OPEN = Set.of(AIR, "minecraft:cave_air", "minecraft:void_air");
    private static final Set<String> FLUID = Set.of("minecraft:water", LAVA, "minecraft:bubble_column",
            "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass");
    private static final List<String> DECORATION_SUFFIXES = List.of("_log", "_wood", "_leaves", "_stem", "_hyphae",
            "_mushroom_block", "_wart_block");

    private FidelityBlocks() {
    }

    static String id(String stateKey) {
        int properties = stateKey.indexOf('[');
        return properties < 0 ? stateKey : stateKey.substring(0, properties);
    }

    static boolean open(String id) {
        return OPEN.contains(id);
    }

    static boolean lava(String id) {
        return LAVA.equals(id);
    }

    static boolean fluid(String id) {
        return FLUID.contains(id);
    }

    static boolean decoration(String id) {
        for (String suffix : DECORATION_SUFFIXES) {
            if (id.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
