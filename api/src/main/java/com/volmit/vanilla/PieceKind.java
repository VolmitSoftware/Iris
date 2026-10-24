package com.volmit.vanilla;

/** How a structure piece was made and how it sits on the terrain. */
public enum PieceKind {
    /** A piece the platform builds in code, not from a jigsaw pool, such as a stronghold room. */
    FIXED,
    /** A jigsaw piece that keeps its own shape, whatever the terrain under it. */
    RIGID,
    /** A jigsaw piece whose blocks follow the terrain's surface, such as a village road. */
    TERRAIN_MATCHING
}
