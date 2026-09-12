package art.arcane.iris.structure.jigsaw;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls whether a configured native jigsaw projects its start onto a Minecraft heightmap.")
public enum IrisJigsawHeightmap {
    @Description("Keeps the registered structure's own heightmap projection exactly as the vanilla, datapack, or mod definition declares it.")
    SOURCE,

    @Description("Removes heightmap projection entirely, so the start piece lands at the structure's literal start height instead of following terrain.")
    NONE,

    @Description("Drops the start piece onto the terrain top counting water as solid, so ocean starts sit on the water surface.")
    WORLD_SURFACE_WG,

    @Description("Identical to WORLD_SURFACE_WG in Iris worlds: the start piece rides the terrain top including water.")
    WORLD_SURFACE,

    @Description("Drops the start piece onto solid terrain, ignoring water, so ocean starts sit on the seabed.")
    OCEAN_FLOOR_WG,

    @Description("Identical to OCEAN_FLOOR_WG in Iris worlds: the start piece sits on solid ground beneath any water.")
    OCEAN_FLOOR,

    @Description("Behaves like WORLD_SURFACE in Iris worlds; the start piece sits on the terrain top including water.")
    MOTION_BLOCKING,

    @Description("Behaves like WORLD_SURFACE in Iris worlds; Iris height queries have no leaf layer to skip.")
    MOTION_BLOCKING_NO_LEAVES
}
