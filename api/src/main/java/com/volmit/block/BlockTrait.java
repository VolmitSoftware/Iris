package com.volmit.block;

/** Host-defined block behavior; tags provide extensible material groups. {@code VINE} is a block that hangs from or
 * clings to the faces around it, such as vines and glow lichen.
 */
public enum BlockTrait {
    AIR, SOLID, OCCLUDING, FLUID, WATER, GRAVITY, FOLIAGE, DECORATION, VINE,
    LOG, LEAVES, ORE, INVENTORY, BLOCK_ENTITY, CUSTOM, NEEDS_UPDATE
}
