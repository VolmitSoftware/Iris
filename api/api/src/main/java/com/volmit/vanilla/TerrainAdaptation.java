package com.volmit.vanilla;

/** How the platform's own definition of a structure adapts the terrain around it when the platform generates the
 * terrain itself. The host generates the terrain, so a module that wants the adaptation does it in its fit.
 */
public enum TerrainAdaptation {
    /** No adaptation. */
    NONE,
    /** Terrain buries the structure's rigid pieces from their ground level, closer horizontally than vertically. */
    BURY,
    /** A thin skirt of terrain grows to meet the structure's base. */
    BEARD_THIN,
    /** A box-shaped skirt of terrain grows to meet the structure's base. */
    BEARD_BOX,
    /** Terrain wraps the structure's rigid pieces on every side. */
    ENCAPSULATE
}
