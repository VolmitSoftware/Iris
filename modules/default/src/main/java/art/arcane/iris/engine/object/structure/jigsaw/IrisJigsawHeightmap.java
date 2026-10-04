package art.arcane.iris.engine.object.structure.jigsaw;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("Controls whether a configured native jigsaw projects its start onto a Minecraft heightmap.")
public enum IrisJigsawHeightmap {
    @Desc("Keeps the registered structure's own heightmap projection exactly as the vanilla, datapack, or mod definition declares it.")
    SOURCE,

    @Desc("Removes heightmap projection entirely, so the start piece lands at the structure's literal start height instead of following terrain.")
    NONE,

    @Desc("Drops the start piece onto the terrain top counting water as solid, so ocean starts sit on the water surface.")
    WORLD_SURFACE_WG,

    @Desc("Identical to WORLD_SURFACE_WG in Iris worlds: the start piece rides the terrain top including water.")
    WORLD_SURFACE,

    @Desc("Drops the start piece onto solid terrain, ignoring water, so ocean starts sit on the seabed.")
    OCEAN_FLOOR_WG,

    @Desc("Identical to OCEAN_FLOOR_WG in Iris worlds: the start piece sits on solid ground beneath any water.")
    OCEAN_FLOOR,

    @Desc("Behaves like WORLD_SURFACE in Iris worlds; the start piece sits on the terrain top including water.")
    MOTION_BLOCKING,

    @Desc("Behaves like WORLD_SURFACE in Iris worlds; Iris height queries have no leaf layer to skip.")
    MOTION_BLOCKING_NO_LEAVES
}
