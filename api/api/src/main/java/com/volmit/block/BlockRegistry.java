package com.volmit.block;

import com.volmit.geometry.BlockFace;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;
import java.util.Optional;

/** What the platform knows about blocks, for one pack snapshot. No native platform objects escape. Methods are safe for
 * concurrent generation calls; predicates must not read the live world.
 *
 * <p>Two ways to get a state: {@link #resolve(BlockSpec)} reads a block the way packs write it (a custom block of the
 * pack, a backup, never failing), {@link #find(String)} asks the platform for exactly one block. Both answer complete
 * states, with every property the platform gives the block, like the states the host hands out; compare against those.
 *
 * <p>A content plugin's block ({@link BlockTrait#CUSTOM}) has the key of the block it is drawn as. The host knows it by
 * the state instance it handed out, from this registry or from a block it read, so pass that instance on as it is, and
 * change it through {@link #withPropertyIfAccepted}, {@link #rotate} and {@link #merge}: a state built from the key is the plain
 * block.
 */
public interface BlockRegistry {
    /** The state {@code spec} names, resolved the way packs expect, so it never throws for a block the platform lacks:
     * a block of the pack's {@code blocks} folder with the spec's properties over its own, else the platform's block
     * (a key without a namespace is {@code minecraft:}) with the spec's properties, else the backup resolved the same
     * way, else air.
     */
    BlockState resolve(BlockSpec spec);

    /** The platform's block {@code key}, such as {@code minecraft:lava} or {@code minecraft:oak_log[axis=y]}, with the
     * properties the key leaves out at the platform's defaults; empty when the platform has no such block or property.
     * For a module that must know whether the platform has a block, unlike {@link #resolve(BlockSpec)}.
     */
    Optional<BlockState> find(String key);

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
     * deepslate variant, one placed in other rock its ordinary one; {@code ore} itself when it has no variant.
     */
    BlockState oreFor(BlockState rock, BlockState ore);

    boolean hasTrait(BlockState state, BlockTrait trait);
    boolean inTag(BlockState state, ResourceId tag);
    /** Whether state can attach to the specified face of support, ignoring other neighbors. */
    boolean canAttach(BlockState state, BlockState support, BlockFace face);

    /** Whether {@code state} can stand on {@code below}: the support rule the host checks before it writes a
     * decoration, such as a flower on grass but not on stone. Only the kinds of block count, not their properties.
     */
    boolean canPlaceOn(BlockState state, BlockState below);

    /**
     * {@code state} turned with an object: every direction the block faces (its facing, axis, connections, or the
     * sixteen directions of a sign) is moved by {@code turn}, as the platform turns stairs, logs, walls and wires. A
     * block without directions comes back unchanged; air when the block cannot face the way it is turned.
     */
    BlockState rotate(BlockState state, BlockTurn turn);

    /**
     * Like {@link #rotate}, but says when the block cannot face the way it is turned instead of answering air, so a
     * caller can tell a block that was dropped from air that stayed air. The default asks {@link #rotate} and takes air
     * from a block that is not air for a block that was dropped.
     */
    default Optional<BlockState> tryRotate(BlockState state, BlockTurn turn) {
        BlockState turned = rotate(state, turn);
        boolean dropped = hasTrait(turned, BlockTrait.AIR) && !hasTrait(state, BlockTrait.AIR);
        return dropped ? Optional.empty() : Optional.of(turned);
    }

    /** {@code update} laid over {@code base}: the platform's merge of two states of one block, such as the sides of a
     * fence, wall or pane; {@code update} when the platform does not merge them.
     */
    BlockState merge(BlockState base, BlockState update);

    /** {@code state} with the property {@code name} set to {@code value}, as the platform sets it. Unlike
     * {@link BlockState#withProperty} it keeps a content plugin's block itself ({@link BlockTrait#CUSTOM}): such a block
     * shares its key with the block it is drawn as, so only the state instance the registry handed out knows it.
     * {@code state} itself when the block has no property {@code name} or does not accept {@code value}, such as a
     * {@code facing} of {@code up} for a block that only faces sideways.
     */
    BlockState withPropertyIfAccepted(BlockState state, String name, String value);

    /** Whether {@code state} matches {@code pattern} as the platform compares them: the same block, and every property
     * {@code pattern} sets equal.
     */
    boolean matches(BlockState state, BlockState pattern);
}
