package art.arcane.iris.nativegen;

import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

public final class NativeTransitionColumn {
    private NativeTransitionColumn() {
    }

    public static NoiseColumn column(TerrainBoundarySignature signature, LevelHeightAccessor height) {
        BlockState[] states = new BlockState[height.getHeight()];
        Map<String, BlockState> palette = new HashMap<>();
        for (int offset = 0; offset < states.length; offset++) {
            String state = signature.geometry().voxelAt(height.getMinY() + offset).stateKey();
            states[offset] = palette.computeIfAbsent(state, NativeTransitionColumn::parse);
        }
        return new NoiseColumn(height.getMinY(), states);
    }

    public static int height(TerrainBoundarySignature signature, Heightmap.Types type, LevelHeightAccessor height) {
        Map<String, BlockState> palette = new HashMap<>();
        for (int offset = height.getHeight() - 1; offset >= 0; offset--) {
            String state = signature.geometry().voxelAt(height.getMinY() + offset).stateKey();
            if (type.isOpaque().test(palette.computeIfAbsent(state, NativeTransitionColumn::parse))) {
                return height.getMinY() + offset + 1;
            }
        }
        return height.getMinY();
    }

    private static BlockState parse(String key) {
        String nativeKey = key;
        if (!key.startsWith("minecraft:") && IrisPlatforms.isBound()) {
            PlatformBlockState logicalState = IrisPlatforms.get().registries().blockOrNull(key);
            PlatformBlockState baseState = logicalState == null ? null : logicalState.placementBaseState();
            if (baseState != null) {
                nativeKey = baseState.key();
            }
        }
        StringReader reader = new StringReader(nativeKey);
        try {
            BlockState state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, reader, false).blockState();
            if (reader.canRead()) {
                throw new IllegalArgumentException("Unexpected terrain block state suffix: " + reader.getRemaining());
            }
            return state;
        } catch (CommandSyntaxException failure) {
            throw new IllegalArgumentException("Saved terrain block state cannot be resolved: " + key, failure);
        }
    }
}
