package com.volmit.block;

/** What a block is, as the platform answers it ({@link BlockRegistry#hasTrait}); tags name further groups
 * ({@link BlockRegistry#inTag}). {@code VINE} is a block that hangs from or clings to the faces around it, such as
 * vines and glow lichen; {@code STORAGE} a chest-like container that keeps items, such as a chest or a barrel;
 * {@code CUSTOM} a block of a content plugin or mod rather than of the platform itself.
 */
public enum BlockTrait {
    AIR, SOLID, OCCLUDING, FLUID, WATER, GRAVITY, FOLIAGE, DECORATION, VINE, LOG, LEAVES, ORE, STORAGE, CUSTOM
}
