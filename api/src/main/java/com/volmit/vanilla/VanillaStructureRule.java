package com.volmit.vanilla;

/** What a module wants for one of the platform's own structures, the ones Minecraft, data packs and mods register
 * (such as {@code minecraft:village_plains}), in a world: whether the platform may start it where it would on its
 * own, and whether the module places it itself.
 */
public enum VanillaStructureRule {
    /** The platform generates the structure where it would; the module has no part in it. */
    GENERATE,
    /** The platform generates the structure where it would, and the module also places it, where it chooses. */
    ALSO_PLACE,
    /** The module places the structure instead of the platform: the platform's own starts of it are blocked. */
    REPLACE,
    /** The platform's own starts of the structure are blocked, and the module does not place it either. */
    BLOCK;

    /** Whether the platform's own starts of the structure are blocked. */
    public boolean blocksPlatform() {
        return this == REPLACE || this == BLOCK;
    }

    /** Whether the module places the structure itself, so the host locates it through the module. */
    public boolean placedByModule() {
        return this == ALSO_PLACE || this == REPLACE;
    }

    /** What two modules' rules for one structure mean together: the platform's starts are blocked when either
     * blocks them, and the structure is placed by a module when either places it. The order does not matter.
     */
    public VanillaStructureRule and(VanillaStructureRule other) {
        return of(blocksPlatform() || other.blocksPlatform(), placedByModule() || other.placedByModule());
    }

    private static VanillaStructureRule of(boolean blocksPlatform, boolean placedByModule) {
        if (blocksPlatform) {
            return placedByModule ? REPLACE : BLOCK;
        }
        return placedByModule ? ALSO_PLACE : GENERATE;
    }
}
