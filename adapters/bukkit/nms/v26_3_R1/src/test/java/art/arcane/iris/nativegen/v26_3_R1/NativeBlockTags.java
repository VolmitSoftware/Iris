package art.arcane.iris.nativegen.v26_3_R1;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;

public final class NativeBlockTags {
    private NativeBlockTags() {
    }

    public static void bindHeightmapFixtures() {
        List<Holder<Block>> solid = List.of(Blocks.STONE.builtInRegistryHolder(),
                Blocks.OAK_LOG.builtInRegistryHolder(), Blocks.SPRUCE_LOG.builtInRegistryHolder());
        List<Holder<Block>> canopy = List.of(Blocks.STONE.builtInRegistryHolder(),
                Blocks.OAK_LOG.builtInRegistryHolder(), Blocks.SPRUCE_LOG.builtInRegistryHolder(),
                Blocks.OAK_LEAVES.builtInRegistryHolder(), Blocks.SPRUCE_LEAVES.builtInRegistryHolder());
        BuiltInRegistries.BLOCK.prepareTagReload(new TagLoader.LoadResult<>(Registries.BLOCK, Map.of(
                BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP, canopy,
                BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES, solid))).apply();
    }
}
