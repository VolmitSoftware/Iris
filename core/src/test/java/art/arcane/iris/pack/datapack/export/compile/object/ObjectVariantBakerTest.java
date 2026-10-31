package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.StructureTemplateEncoder;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class ObjectVariantBakerTest {
    private static final String NAMESPACE = "iris_export";
    private static final String LEAVES = "minecraft:oak_leaves[distance=1,persistent=true,waterlogged=false]";
    private static final long RIGHT_ANGLE_GOLDEN = 0x5db739a5b940bf1dL;

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

        BakedVariant first = new ObjectVariantBaker().bake(id, new ObjectSource(tree, 0, TreeRoots.NONE), spec);
        BakedVariant second = new ObjectVariantBaker().bake(id, new ObjectSource(tree, 0, TreeRoots.NONE), spec);

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
                .bake("iris_export:object/rock/a", new ObjectSource(volume, 0, TreeRoots.NONE), VariantSpec.builder().build());

        assertEquals(List.of("minecraft:stone"), states(variant.volume()));
        assertEquals(2, variant.volume().sizeX());
        assertEquals(2, variant.volume().sizeY());
        assertEquals(0, variant.baseY());
    }

    @Test
    public void plantsWithNothingUnderThemAreLeftOut() {
        TemplateVolume reef = new TemplateVolume(3, 4, 3, List.of(
                block(1, 0, 1, "minecraft:sea_pickle[pickles=1,waterlogged=false]"),
                block(0, 0, 0, "minecraft:tube_coral_block"),
                block(0, 1, 0, "minecraft:tube_coral_block"),
                block(0, 2, 0, "minecraft:sea_pickle[pickles=2,waterlogged=false]"),
                block(2, 2, 2, "minecraft:sea_pickle[pickles=3,waterlogged=false]"),
                block(2, 1, 0, "minecraft:tube_coral_fan[waterlogged=false]"),
                block(1, 1, 0, "minecraft:tube_coral_wall_fan[facing=east,waterlogged=false]"),
                block(1, 2, 0, "minecraft:tube_coral_wall_fan[facing=west,waterlogged=false]"),
                block(2, 3, 2, "minecraft:tube_coral_block")));

        BakedVariant variant = new ObjectVariantBaker()
                .bake("iris_export:object/reef/a", new ObjectSource(reef, 0, TreeRoots.NONE), VariantSpec.builder().build());

        Set<String> kept = new HashSet<>();
        for (TemplateBlock block : variant.volume().blocks()) {
            kept.add(block.x() + "," + block.y() + "," + block.z() + " " + block.state().name());
        }
        assertEquals(Set.of("1,0,1 minecraft:sea_pickle", "0,0,0 minecraft:tube_coral_block", "0,1,0 minecraft:tube_coral_block",
                "0,2,0 minecraft:sea_pickle", "1,1,0 minecraft:tube_coral_wall_fan", "2,3,2 minecraft:tube_coral_block"), kept);
    }

    @Test
    public void quarterRotationMovesBlocksAndFacing() {
        TemplateVolume volume = new TemplateVolume(3, 1, 3, List.of(
                block(1, 0, 1, "minecraft:stone"),
                block(1, 0, 0, "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]")));
        VariantSpec spec = VariantSpec.builder().rotation(new VariantSpec.Rotation(0, 90, 0)).build();

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/s/a", new ObjectSource(volume, 0, TreeRoots.NONE), spec);

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

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/f/a", new ObjectSource(volume, 0, TreeRoots.NONE), spec);

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

        BakedVariant big = new ObjectVariantBaker().bake("iris_export:object/l/a", new ObjectSource(volume, 0, TreeRoots.NONE), scaled);

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

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/floor/a", new ObjectSource(floor, 0, TreeRoots.NONE),
                VariantSpec.builder().edits(List.of(always, half)).build());

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
        BakedVariant low = new BakedVariant("iris_export:object/a/low", new TemplateVolume(1, 3, 1, List.of(block(0, 0, 0, "minecraft:stone"))), -2, TreeRoots.NONE);
        BakedVariant flat = new BakedVariant("iris_export:object/a/flat", new TemplateVolume(1, 1, 1, List.of(block(0, 0, 0, "minecraft:stone"))), 0, TreeRoots.NONE);

        List<BakedVariant> aligned = ObjectVariantBaker.alignBases(List.of(low, flat));

        assertEquals(low, aligned.get(0));
        assertEquals(-2, aligned.get(1).baseY());
        assertEquals("iris_export:object/a/flat_lift2", aligned.get(1).id());
        assertEquals(2, aligned.get(1).volume().blocks().getFirst().y());
        assertEquals(3, aligned.get(1).volume().sizeY());
        assertFalse(aligned.get(1).volume().blocks().isEmpty());
    }

    @Test
    public void rootsSurviveAnUnchangedBake() {
        TemplateVolume volume = new TemplateVolume(3, 2, 3, List.of(
                block(1, 0, 1, "minecraft:oak_wood[axis=y]"),
                block(2, 0, 1, "minecraft:oak_wood[axis=y]"),
                block(1, 1, 1, "minecraft:oak_log[axis=y]")));
        TreeRoots roots = new TreeRoots(List.of(block(1, 0, 1, "minecraft:oak_wood[axis=y]"),
                block(2, 0, 1, "minecraft:oak_wood[axis=y]")), 3);

        BakedVariant variant = new ObjectVariantBaker()
                .bake("iris_export:object/tree/a", new ObjectSource(volume, 0, roots), VariantSpec.builder().build());

        assertEquals(roots, variant.roots());
    }

    @Test
    public void rootsTurnWithTheirBlocks() {
        TemplateVolume volume = new TemplateVolume(3, 2, 3, List.of(
                block(1, 0, 1, "minecraft:oak_wood[axis=y]"),
                block(2, 0, 1, "minecraft:spruce_wood[axis=x]"),
                block(1, 1, 1, "minecraft:oak_log[axis=y]")));
        TreeRoots roots = new TreeRoots(List.of(block(2, 0, 1, "minecraft:spruce_wood[axis=x]")), 2);
        VariantSpec spec = VariantSpec.builder().rotation(new VariantSpec.Rotation(0, 90, 0)).build();

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/tree/b", new ObjectSource(volume, 0, roots), spec);

        TemplateBlock moved = variant.volume().blocks().stream()
                .filter(block -> block.state().name().equals("minecraft:spruce_wood")).findFirst().orElseThrow();
        assertEquals(1, variant.roots().cells().size());
        TemplateBlock root = variant.roots().cells().getFirst();
        assertEquals(List.of(moved.x(), moved.y(), moved.z()), List.of(root.x(), root.y(), root.z()));
        assertEquals("z", root.state().properties().get("axis"));
        assertEquals(2, variant.roots().reach());
    }

    @Test
    public void scaledRootsKeepTheLowestCellPerColumnAndStretchTheirReach() {
        TemplateVolume volume = new TemplateVolume(2, 2, 1, List.of(
                block(0, 0, 0, "minecraft:oak_wood[axis=y]"),
                block(0, 1, 0, "minecraft:oak_log[axis=y]"),
                block(1, 1, 0, "minecraft:oak_log[axis=y]")));
        TreeRoots roots = new TreeRoots(List.of(block(0, 0, 0, "minecraft:oak_wood[axis=y]")), 3);

        BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/tree/c", new ObjectSource(volume, 0, roots),
                VariantSpec.builder().scale(2).build());

        assertEquals(6, variant.roots().reach());
        assertEquals(4, variant.roots().cells().size());
        for (TemplateBlock cell : variant.roots().cells()) {
            assertEquals(0, cell.y());
            assertEquals("minecraft:oak_wood", cell.state().name());
        }
    }

    @Test
    public void alignedBasesLiftRoots() {
        TreeRoots roots = new TreeRoots(List.of(block(0, 0, 0, "minecraft:oak_wood[axis=y]")), 2);
        BakedVariant low = new BakedVariant("iris_export:object/r/low", new TemplateVolume(1, 3, 1, List.of(block(0, 0, 0, "minecraft:stone"))), -2, TreeRoots.NONE);
        BakedVariant rooted = new BakedVariant("iris_export:object/r/rooted", new TemplateVolume(1, 1, 1,
                List.of(block(0, 0, 0, "minecraft:oak_wood[axis=y]"))), 0, roots);

        BakedVariant lifted = ObjectVariantBaker.alignBases(List.of(low, rooted)).get(1);

        assertEquals(2, lifted.roots().cells().getFirst().y());
        assertEquals(2, lifted.roots().reach());
    }

    @Test
    public void diagonalTurnsFillSolidCanopiesWithoutHoles() {
        for (double degrees : new double[]{45, 135}) {
            assertSolid(leafDisc(6, 3), degrees, (x, z) -> x * x + z * z <= 25D);
            assertSolid(leafBox(4, 3), degrees, (x, z) -> Math.abs(x) <= 3.75D && Math.abs(z) <= 3.75D);
        }
    }

    @Test
    public void diagonalTurnsKeepEveryThinTrunkColumn() {
        int[][] columns = {{2, 0}, {0, 2}, {-2, 0}, {3, 1}};
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int[] column : columns) {
            for (int y = 0; y < 6; y++) {
                blocks.add(block(column[0] + 3, y, column[1] + 3, "minecraft:oak_log[axis=y]"));
            }
        }
        TemplateVolume trunks = new TemplateVolume(7, 6, 7, blocks);
        for (double degrees : new double[]{45, 135, 225, 315}) {
            BakedVariant variant = turned(trunks, new VariantSpec.Rotation(0, degrees, 0));
            Map<Long, int[]> heights = new HashMap<>();
            for (TemplateBlock block : variant.volume().blocks()) {
                int x = anchoredX(variant, block);
                int z = anchoredZ(variant, block);
                heights.computeIfAbsent(cell(x, 0, z), ignored -> new int[]{x, z, 0})[2]++;
            }
            double cos = Math.cos(Math.toRadians(degrees));
            double sin = Math.sin(Math.toRadians(degrees));
            for (int[] column : columns) {
                long forward = cell((int) Math.round(cos * column[0] + sin * column[1]), 0,
                        (int) Math.round(-sin * column[0] + cos * column[1]));
                boolean kept = false;
                for (Map.Entry<Long, int[]> output : heights.entrySet()) {
                    int[] value = output.getValue();
                    assertEquals(degrees + " degrees cut a trunk column short", 6, value[2]);
                    boolean samples = (int) Math.round(cos * value[0] - sin * value[1]) == column[0]
                            && (int) Math.round(sin * value[0] + cos * value[1]) == column[1];
                    kept |= samples || output.getKey() == forward;
                }
                assertTrue(degrees + " degrees lost the trunk column at " + column[0] + "," + column[1], kept);
            }
        }
    }

    @Test
    public void rightAngleTurnsKeepTheirExactLayout() {
        CompoundTag chest = new CompoundTag();
        chest.putString("id", "minecraft:chest");
        TemplateVolume volume = new TemplateVolume(4, 4, 4, List.of(
                block(0, 0, 0, "minecraft:stone"),
                block(3, 0, 1, "minecraft:stone"),
                block(1, 1, 2, "minecraft:oak_log[axis=x]"),
                block(2, 2, 0, "minecraft:oak_stairs[facing=north,half=bottom,shape=outer_left,waterlogged=false]"),
                new TemplateBlock(3, 1, 3, VanillaBlockState.parse("minecraft:chest[facing=east,type=single,waterlogged=false]"), chest),
                block(1, 3, 1, "minecraft:oak_leaves[distance=1,persistent=false,waterlogged=false]"),
                block(0, 2, 3, "minecraft:oak_slab[type=bottom,waterlogged=false]")));
        TreeRoots roots = new TreeRoots(List.of(block(0, 0, 0, "minecraft:stone")), 2);
        StringBuilder layout = new StringBuilder();
        for (double scale : new double[]{1, 1.5}) {
            for (int x = 0; x < 360; x += 90) {
                for (int y = 0; y < 360; y += 90) {
                    for (int z = 0; z < 360; z += 90) {
                        VariantSpec spec = VariantSpec.builder().scale(scale).rotation(new VariantSpec.Rotation(x, y, z)).build();
                        describe(layout, new ObjectVariantBaker().bake("iris_export:object/turn/a", new ObjectSource(volume, -1, roots), spec));
                    }
                }
            }
        }

        assertEquals(RIGHT_ANGLE_GOLDEN, fnv(layout));
    }

    private static void assertSolid(TemplateVolume source, double degrees, Interior interior) {
        BakedVariant variant = turned(source, new VariantSpec.Rotation(0, degrees, 0));
        Set<Long> covered = new HashSet<>();
        for (TemplateBlock block : variant.volume().blocks()) {
            assertTrue(degrees + " degrees wrote a cell twice",
                    covered.add(cell(anchoredX(variant, block), anchoredY(variant, block), anchoredZ(variant, block))));
        }
        double cos = Math.cos(Math.toRadians(degrees));
        double sin = Math.sin(Math.toRadians(degrees));
        int reach = Math.max(source.sizeX(), source.sizeZ());
        for (int y = 0; y < source.sizeY(); y++) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    if (interior.contains(cos * x - sin * z, sin * x + cos * z)) {
                        assertTrue(degrees + " degrees left a hole at " + x + "," + y + "," + z, covered.contains(cell(x, y, z)));
                    }
                }
            }
        }
    }

    private static BakedVariant turned(TemplateVolume source, VariantSpec.Rotation rotation) {
        return new ObjectVariantBaker().bake("iris_export:object/turn/b", new ObjectSource(source, 0, TreeRoots.NONE),
                VariantSpec.builder().rotation(rotation).build());
    }

    private static int anchoredX(BakedVariant variant, TemplateBlock block) {
        return block.x() - (variant.volume().sizeX() - 1) / 2;
    }

    private static int anchoredY(BakedVariant variant, TemplateBlock block) {
        return block.y() + variant.baseY();
    }

    private static int anchoredZ(BakedVariant variant, TemplateBlock block) {
        return block.z() - (variant.volume().sizeZ() - 1) / 2;
    }

    private static TemplateVolume leafDisc(int radius, int height) {
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z <= radius * radius) {
                        blocks.add(block(x + radius, y, z + radius, LEAVES));
                    }
                }
            }
        }
        return new TemplateVolume(radius * 2 + 1, height, radius * 2 + 1, blocks);
    }

    private static TemplateVolume leafBox(int half, int height) {
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    blocks.add(block(x + half, y, z + half, LEAVES));
                }
            }
        }
        return new TemplateVolume(half * 2 + 1, height, half * 2 + 1, blocks);
    }

    private static void describe(StringBuilder layout, BakedVariant variant) {
        TemplateVolume volume = variant.volume();
        layout.append(variant.id()).append(' ').append(volume.sizeX()).append('x').append(volume.sizeY()).append('x')
                .append(volume.sizeZ()).append(" base=").append(variant.baseY()).append('\n');
        for (TemplateBlock block : volume.blocks()) {
            layout.append(block.x()).append(',').append(block.y()).append(',').append(block.z()).append(' ')
                    .append(block.state().canonical()).append(' ')
                    .append(block.nbt() == null ? "-" : block.nbt().getString("id")).append('\n');
        }
        layout.append("roots reach=").append(variant.roots().reach()).append('\n');
        for (TemplateBlock cell : variant.roots().cells()) {
            layout.append(cell.x()).append(',').append(cell.y()).append(',').append(cell.z()).append(' ')
                    .append(cell.state().canonical()).append('\n');
        }
    }

    private static long fnv(CharSequence text) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < text.length(); index++) {
            hash ^= text.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long cell(int x, int y, int z) {
        return (((long) x & 0x1FFFFFL) << 42) | (((long) y & 0x1FFFFFL) << 21) | ((long) z & 0x1FFFFFL);
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

    private interface Interior {
        boolean contains(double x, double z);
    }
}
