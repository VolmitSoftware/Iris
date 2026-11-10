/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded.structure;

import com.mojang.datafixers.util.Pair;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.JigsawStructureInfo;
import com.volmit.tooling.StructureTemplate;
import com.volmit.tooling.TemplateBlock;
import com.volmit.tooling.TemplatePool;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The records the tools read from vanilla data: the values the Bukkit host reads from the same data. */
public class PlatformStructuresVanillaTest {
    private static final String VILLAGE = "minecraft:village_plains";
    private static final String VILLAGE_START_POOL = "minecraft:village/plains/town_centers";
    private static final int VILLAGE_DEPTH = 6;
    private static final int VILLAGE_DISTANCE = 80;

    private static HolderLookup.Provider vanilla;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        vanilla = VanillaRegistries.createLookup();
    }

    private static ListTag intsOf(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private static CompoundTag blockOf(int state, int x, CompoundTag nbt) {
        CompoundTag block = new CompoundTag();
        block.put("pos", intsOf(x, 0, 0));
        block.putInt("state", state);
        if (nbt != null) {
            block.put("nbt", nbt);
        }
        return block;
    }

    /** A template of a stone block and a jigsaw block whose final state is oak planks, in the form a template file stores. */
    private static CompoundTag templateTag() {
        CompoundTag finalState = new CompoundTag();
        finalState.putString("final_state", "minecraft:oak_planks");
        finalState.putString("id", "minecraft:jigsaw");
        finalState.putString("name", "minecraft:empty");
        finalState.putString("target", "minecraft:empty");
        finalState.putString("pool", "minecraft:empty");
        finalState.putString("joint", "rollable");
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.JIGSAW.defaultBlockState()));
        ListTag blocks = new ListTag();
        blocks.add(blockOf(0, 0, null));
        blocks.add(blockOf(1, 1, finalState));
        CompoundTag tag = new CompoundTag();
        tag.put("size", intsOf(2, 1, 1));
        tag.put("palette", palette);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static <T> T registered(ResourceKey<net.minecraft.core.Registry<T>> registry, String key) {
        return vanilla.lookupOrThrow(registry)
                .getOrThrow(ResourceKey.create(registry, Identifier.parse(key))).value();
    }

    @Test
    public void aVanillaJigsawStructureReadsAsItsStartPoolDepthAndDistance() {
        JigsawStructure village = (JigsawStructure) registered(Registries.STRUCTURE, VILLAGE);
        Identifier startPool = village.getStartPool().unwrapKey().orElseThrow().identifier();

        JigsawStructureInfo info = ModdedPlatformStructures.jigsawInfoOf(ResourceId.parse(VILLAGE), village, startPool, "26.2");

        assertEquals(ResourceId.parse(VILLAGE_START_POOL), info.startPool());
        assertEquals(VILLAGE_DEPTH, info.maxDepth());
        assertEquals(VILLAGE_DISTANCE, info.maxDistance());
        assertEquals("26.2", info.gameVersion());
    }

    @Test
    public void theShuffleSeedIsTheHashBukkitGivesTheSameKey() {
        long bukkitHash = 31 * "minecraft".hashCode() + "village_plains".hashCode();

        assertEquals(bukkitHash, ModdedPlatformStructures.shuffleSeedOf(ResourceId.parse(VILLAGE)));
    }

    @Test
    public void aVanillaPoolReadsAsWeightedElementsWithTheirTemplates() {
        StructureTemplatePool pool = registered(Registries.TEMPLATE_POOL, VILLAGE_START_POOL);

        assertTrue("pool " + pool + " has " + pool.getTemplates().size(), !pool.getTemplates().isEmpty());
        for (Pair<StructurePoolElement, Integer> pair : pool.getTemplates()) {
            TemplatePool.Member member = PoolReading.memberOf(pair, null);
            TemplatePool.Element element = (TemplatePool.Element) member;
            assertEquals(Math.max(1, pair.getSecond()), element.weight());
            assertEquals("minecraft", element.template().orElseThrow().namespace());
            assertTrue("template of " + element, element.template().orElseThrow().path().startsWith("village/plains/"));
        }
    }

    @Test
    public void aTemplateReadsAsItsSizeBlocksAndJigsawFinalState() throws Exception {
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template =
                new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate();
        template.load(BuiltInRegistries.BLOCK, templateTag());

        StructureTemplate read = TemplateReading.read(template).orElseThrow();

        assertEquals(new BlockPosition(2, 1, 1), read.size());
        assertEquals(1, read.paletteCount());
        assertEquals(0, read.entityCount());
        assertEquals(2, read.blocks().size());
        TemplateBlock stone = read.blocks().get(0);
        assertEquals(new BlockPosition(0, 0, 0), stone.position());
        assertEquals(BlockState.parse("minecraft:stone"), stone.state());
        assertEquals(Optional.empty(), stone.jigsawFinalState());
        TemplateBlock jigsaw = read.blocks().get(1);
        assertEquals(new BlockPosition(1, 0, 0), jigsaw.position());
        assertEquals(BlockState.parse("minecraft:jigsaw[orientation=north_up]"), jigsaw.state());
        assertEquals(Optional.of("minecraft:oak_planks"), jigsaw.jigsawFinalState());
        assertEquals(Optional.empty(), jigsaw.blockEntity());
    }
}
