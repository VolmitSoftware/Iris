package art.arcane.iris.core.service;

import art.arcane.iris.engine.framework.structure.assembly.PieceTurn;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;

import java.util.Set;

/** The few block facts the studio needs, asked of api block states. */
public final class JigsawStudioBlocks {
    public static final BlockState AIR = BlockState.parse("minecraft:air");
    public static final String JIGSAW_BLOCK = "minecraft:jigsaw";
    public static final String ORIENTATION_PROPERTY = "orientation";
    private static final String CHEST_BLOCK = "minecraft:chest";
    private static final String STRUCTURE_VOID_BLOCK = "minecraft:structure_void";
    private static final Set<String> AIR_BLOCKS = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private JigsawStudioBlocks() {
    }

    public static boolean isAir(BlockState state) {
        return AIR_BLOCKS.contains(state.material().toString());
    }

    public static boolean isJigsaw(BlockState state) {
        return JIGSAW_BLOCK.equals(state.material().toString());
    }

    public static boolean isChest(BlockState state) {
        return CHEST_BLOCK.equals(state.material().toString());
    }

    public static boolean isStructureVoid(BlockState state) {
        return STRUCTURE_VOID_BLOCK.equals(state.material().toString());
    }

    public static BlockState jigsawMarker(String orientation) {
        return BlockState.parse(JIGSAW_BLOCK + "[" + ORIENTATION_PROPERTY + "=" + orientation + "]");
    }

    /** {@code state} turned like {@code rotation}; null when the registry cannot turn the block. */
    public static BlockState rotated(BlockRegistry registry, BlockState state, PieceTurn rotation) {
        if (rotation.quarterTurns() == 0) {
            return state;
        }
        return registry.tryRotate(state, rotation.blockTurn()).orElse(null);
    }
}
