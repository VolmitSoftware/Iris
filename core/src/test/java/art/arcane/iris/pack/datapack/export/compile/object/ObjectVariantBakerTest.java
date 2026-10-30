package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.StructureTemplateEncoder;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ObjectVariantBakerTest {
    private static final String NAMESPACE = "iris_export";

    @Test
    public void variantBakingIsDeterministic() throws IOException {
        TemplateVolume tree = tree();
        ObjectEdit chanceEdit = new ObjectEdit(List.of(VanillaBlockState.parse("minecraft:oak_leaves")), false,
                List.of(new WeightedState(VanillaBlockState.parse("minecraft:birch_leaves"), 1),
                        new WeightedState(VanillaBlockState.parse("minecraft:azalea_leaves"), 3)), 0.5);
        VariantSpec spec = VariantSpec.builder()
                .scale(1.5)
                .rotation(new VariantSpec.Rotation(0, 45, 0))
                .warp(new VariantWarp("simplex", 2, (x, y, z) -> Math.sin(x * 0.3) * 2))
                .snow(0.6)
                .edits(List.of(chanceEdit))
                .build();
        String id = ObjectVariantBaker.templateId(NAMESPACE, "trees/oak", spec, DataVersion.V26_3);

        BakedVariant first = new ObjectVariantBaker().bake(id, tree, spec, "trees/oak", new ArrayList<>());
        BakedVariant second = new ObjectVariantBaker().bake(id, tree, spec, "trees/oak", new ArrayList<>());

        assertEquals(first, second);
        assertEquals(id, ObjectVariantBaker.templateId(NAMESPACE, "trees/oak", spec, DataVersion.V26_3));
        assertTrue(id.startsWith("iris_export:object/trees/oak/"));
        assertArrayEquals(new StructureTemplateEncoder().encode(first.volume(), DataVersion.V26_3),
                new StructureTemplateEncoder().encode(second.volume(), DataVersion.V26_3));
        VariantSpec otherAngle = VariantSpec.builder().rotation(new VariantSpec.Rotation(0, 30, 0)).build();
        assertNotEquals(id, ObjectVariantBaker.templateId(NAMESPACE, "trees/oak", otherAngle, DataVersion.V26_3));
    }

    @Test
    public void unchangedVariantKeepsSizeAndDropsAir() {
        TemplateVolume volume = new TemplateVolume(2, 2, 1, List.of(
                block(0, 0, 0, "minecraft:stone"),
                block(1, 0, 0, "minecraft:air"),
                block(0, 1, 0, "minecraft:cave_air")));

        BakedVariant variant = new ObjectVariantBaker()
                .bake("iris_export:object/rock/a", volume, VariantSpec.builder().build(), "rock", new ArrayList<>());

        assertEquals(List.of("minecraft:stone"), states(variant.volume()));
        assertEquals(2, variant.volume().sizeX());
        assertEquals(2, variant.volume().sizeY());
        assertEquals(0, variant.baseY());
    }

    @Test
    public void quarterRotationMovesBlocksAndFacing() {
        TemplateVolume volume = new TemplateVolume(3, 1, 3, List.of(
                block(1, 0, 1, "minecraft:stone"),
                block(1, 0, 0, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")));
        VariantSpec spec = VariantSpec.builder().rotation(new VariantSpec.Rotation(0, 90, 0)).build();

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/s/a", volume, spec, "s", new ArrayList<>());

        TemplateBlock stairs = variant.volume().blocks().stream()
                .filter(block -> block.state().name().equals("minecraft:oak_stairs")).findFirst().orElseThrow();
        assertEquals("west", stairs.state().properties().get("facing"));
        assertEquals(0, stairs.x());
        assertEquals(0, stairs.z());
    }

    @Test
    public void verticalFlipFlipsHalves() {
        TemplateVolume volume = new TemplateVolume(1, 2, 1, List.of(
                block(0, 0, 0, "minecraft:stone"),
                block(0, 1, 0, "minecraft:oak_slab[type=bottom,waterlogged=false]")));
        VariantSpec spec = VariantSpec.builder().rotation(new VariantSpec.Rotation(180, 0, 0)).build();

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/f/a", volume, spec, "f", new ArrayList<>());

        TemplateBlock slab = variant.volume().blocks().getFirst();
        assertEquals("minecraft:oak_slab", slab.state().name());
        assertEquals("top", slab.state().properties().get("type"));
        assertEquals(0, slab.y());
    }

    @Test
    public void scaleGrowsTheTemplate() {
        TemplateVolume volume = new TemplateVolume(2, 2, 2, List.of(
                block(0, 0, 0, "minecraft:oak_log[axis=y]"),
                block(1, 1, 1, "minecraft:oak_log[axis=y]")));
        VariantSpec scaled = VariantSpec.builder().scale(2).build();

        BakedVariant big = new ObjectVariantBaker().bake("iris_export:object/l/a", volume, scaled, "l", new ArrayList<>());

        assertEquals(16, big.volume().blocks().size());
        assertEquals(4, big.volume().sizeY());
    }

    @Test
    public void deterministicEditsBakeAndChanceEditsRoll() {
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                blocks.add(block(x, 0, z, "minecraft:cobblestone"));
            }
        }
        TemplateVolume floor = new TemplateVolume(16, 1, 16, blocks);
        ObjectEdit always = new ObjectEdit(List.of(VanillaBlockState.parse("minecraft:cobblestone")), false,
                List.of(new WeightedState(VanillaBlockState.parse("minecraft:stone_bricks"), 1)), 1);
        ObjectEdit half = new ObjectEdit(List.of(VanillaBlockState.parse("minecraft:stone_bricks")), false,
                List.of(new WeightedState(VanillaBlockState.parse("minecraft:mossy_stone_bricks"), 1)), 0.5);

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/floor/a", floor,
                VariantSpec.builder().edits(List.of(always, half)).build(), "floor", new ArrayList<>());

        Set<String> names = new HashSet<>();
        int mossy = 0;
        for (TemplateBlock block : variant.volume().blocks()) {
            names.add(block.state().name());
            if (block.state().name().equals("minecraft:mossy_stone_bricks")) {
                mossy++;
            }
        }
        assertEquals(Set.of("minecraft:stone_bricks", "minecraft:mossy_stone_bricks"), names);
        assertTrue(mossy > 96 && mossy < 160);
    }

    @Test
    public void basesAlignAcrossVariants() {
        BakedVariant low = new BakedVariant("iris_export:object/a/low", new TemplateVolume(1, 3, 1, List.of(block(0, 0, 0, "minecraft:stone"))), -2);
        BakedVariant flat = new BakedVariant("iris_export:object/a/flat", new TemplateVolume(1, 1, 1, List.of(block(0, 0, 0, "minecraft:stone"))), 0);

        List<BakedVariant> aligned = ObjectVariantBaker.alignBases(List.of(low, flat));

        assertEquals(low, aligned.get(0));
        assertEquals(-2, aligned.get(1).baseY());
        assertEquals("iris_export:object/a/flat_lift2", aligned.get(1).id());
        assertEquals(2, aligned.get(1).volume().blocks().getFirst().y());
        assertEquals(3, aligned.get(1).volume().sizeY());
        assertFalse(aligned.get(1).volume().blocks().isEmpty());
    }

    private static TemplateVolume tree() {
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int y = 0; y < 5; y++) {
            blocks.add(block(2, y, 2, "minecraft:oak_log[axis=y]"));
        }
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (x != 2 || z != 2) {
                    blocks.add(block(x, 3, z, "minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]"));
                }
            }
        }
        return new TemplateVolume(5, 5, 5, blocks);
    }

    private static TemplateBlock block(int x, int y, int z, String state) {
        return new TemplateBlock(x, y, z, VanillaBlockState.parse(state), null);
    }

    private static List<String> states(TemplateVolume volume) {
        List<String> states = new ArrayList<>();
        for (TemplateBlock block : volume.blocks()) {
            states.add(block.state().canonical());
        }
        return states;
    }
}
