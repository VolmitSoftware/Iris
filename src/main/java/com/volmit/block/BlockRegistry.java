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

    /** The picker for {@code spec}: every block counts as often as its weight, and the entry at a position is chosen by
     * the style's noise at (x, y, z) divided by the zoom, so the palette changes smoothly over the world; a palette of
     * one block always gives it. Blocks resolve as {@link #resolve(BlockSpec)}. Seed the noise like
     * {@link com.volmit.noise.NoiseFactory#sampler}: from the world seed when the palette must line up across chunks.
     * The host keeps what it builds for the pack snapshot, so asking again for the same palette and seed is cheap.
     *
     * @throws IllegalArgumentException when no block of the palette has a weight above zero, which includes an empty list
     */
    PaletteSampler palette(PaletteSpec spec, long seed);

    /** The ore that suits the {@code rock} it is placed in, which the platform decides: an ore placed in deepslate is its
     * deepslate variant, one placed in other rock its ordinary one. {@code ore} itself when it has no variant, and by
     * default for a registry that knows none.
     */
    default BlockState oreFor(BlockState rock, BlockState ore) {
        return ore;
    }

    boolean hasTrait(BlockState state, BlockTrait trait);
    boolean inTag(BlockState state, ResourceId tag);
    /** Whether state can attach to the specified face of support, ignoring other neighbors. */
    boolean canAttach(BlockState state, BlockState support, BlockFace face);

    /** Whether {@code state} can stand on {@code below}: the support rule the host checks before it writes a
     * decoration, such as a flower on grass but not on stone. Only the kinds of block count, not their properties.
     */
    boolean canPlaceOn(BlockState state, BlockState below);
}
