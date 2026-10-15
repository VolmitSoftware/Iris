package com.volmit.world;

/** What a biome is generated as, which decides what grows and spawns in it: the host picks the surface biome of a column
 * from the lists of its region (land, sea or shore biomes), and a biome picked for a cave is a {@link #CAVE} biome.
 * A child biome is the kind of the biome it was found in.
 */
public enum BiomeKind { LAND, SEA, SHORE, CAVE }
