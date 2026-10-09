package com.volmit.block;

import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;
import java.util.Optional;

/** Host adapter for resolved states and cached behavior. No native platform objects escape.
 * Unknown materials, unsupported properties, and malformed states throw IllegalArgumentException.
 * Methods are safe for concurrent generation calls; predicates must not read the live world.
 */
public interface BlockRegistry {
    BlockState resolve(String key);
    default BlockState resolve(BlockState state) { return resolve(state.key()); }

    /** The state {@code spec} names, resolved the way packs expect, so it never throws for a block the platform lacks:
     * a block of the pack's {@code blocks} folder with the spec's properties over its own, else the platform's block
     * (a key without a namespace is {@code minecraft:}) with the spec's properties, else the backup resolved the same
     * way, else air.
     */
    BlockState resolve(BlockSpec spec);

    /** The block entity {@code spec}'s {@code tileData} describes, in the host's format; empty when the spec has none
     * or its resolved block has no block entity.
     */
    Optional<BlockEntityData> blockEntity(BlockSpec spec);

    boolean hasTrait(BlockState state, BlockTrait trait);
    boolean inTag(BlockState state, ResourceId tag);
    /** Whether state can attach to the specified face of support, ignoring other neighbors. */
    boolean canAttach(BlockState state, BlockState support, BlockFace face);
}
