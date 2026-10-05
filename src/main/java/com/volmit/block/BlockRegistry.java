package com.volmit.block;

import com.volmit.resource.ResourceId;

/** Host adapter for resolved states and cached behavior. No native platform objects escape.
 * Unknown materials, unsupported properties, and malformed states throw IllegalArgumentException.
 * Methods are safe for concurrent generation calls; predicates must not read the live world.
 */
public interface BlockRegistry {
    BlockState resolve(String key);
    default BlockState resolve(BlockState state) { return resolve(state.key()); }
    boolean hasTrait(BlockState state, BlockTrait trait);
    boolean inTag(BlockState state, ResourceId tag);
    /** Whether state can attach to the specified face of support, ignoring other neighbors. */
    boolean canAttach(BlockState state, BlockState support, BlockFace face);
}
