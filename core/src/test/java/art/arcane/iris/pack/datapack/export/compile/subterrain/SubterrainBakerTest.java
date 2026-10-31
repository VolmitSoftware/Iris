package art.arcane.iris.pack.datapack.export.compile.subterrain;

import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SubterrainBakerTest {
    private static final long SEED = 7331L;
    private static final VanillaBlockState CAVE_AIR = VanillaBlockState.parse("minecraft:cave_air");
    private static final VanillaBlockState WATER = VanillaBlockState.parse("minecraft:water");
    private static final VanillaBlockState LAVA = VanillaBlockState.parse("minecraft:lava");
    private static final VanillaBlockState STONE = VanillaBlockState.parse("minecraft:stone");
    private static final VanillaBlockState GRASS = VanillaBlockState.parse("minecraft:grass_block");

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void bakedRoomsMatchIrisCells() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.CENOTE));
        try {
            IrisSubterrainFeature feature = feature(data, 0);
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);

            BakedFeature baked = new SubterrainBaker(context, SEED).bake(feature, "pools").orElseThrow();

            assertEquals(SubterrainBaker.VARIANTS, baked.rooms().size());
            SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), SEED,
                    SubterrainTestPacks.MIN_Y, SubterrainTestPacks.MAX_Y, (x, z) -> ""));
            Set<String> kinds = new TreeSet<>();
            for (BakedRoom room : baked.rooms()) {
                SubterrainPlan plan = plan(planner, room);
                Map<Long, VanillaBlockState> core = index(room.core());
                Map<Long, VanillaBlockState> shell = index(room.shell());
                assertEquals(plan.centerX() - room.originX(), room.anchorX());
                assertEquals(plan.centerY() - room.originY(), room.anchorY());
                assertEquals(plan.centerZ() - room.originZ(), room.anchorZ());
                for (int x = 0; x < room.core().sizeX(); x++) {
                    for (int y = 0; y < room.core().sizeY(); y++) {
                        for (int z = 0; z < room.core().sizeZ(); z++) {
                            SubterrainCell cell = planner.sample(List.of(plan), room.originX() + x, room.originY() + y, room.originZ() + z);
                            long key = key(x, y, z);
                            boolean anchor = x == room.anchorX() && y == room.anchorY() && z == room.anchorZ();
                            VanillaBlockState expectedCore = switch (cell.kind()) {
                                case AIR -> CAVE_AIR;
                                case WATER -> WATER;
                                case LAVA -> LAVA;
                                case SOLID -> cell.room().reservedSolid() ? STONE : null;
                                case OUTSIDE -> null;
                            };
                            VanillaBlockState expectedShell = cell.solid() && !cell.room().reservedSolid() ? STONE : null;
                            kinds.add(cell.kind() + (cell.solid() && cell.room().reservedSolid() ? "_RESERVED" : ""));
                            if (anchor) {
                                assertEquals(expectedCore == null ? SubterrainBaker.NO_BLOCK : expectedCore, room.anchorState());
                                assertFalse(core.containsKey(key));
                            } else {
                                assertEquals(x + "," + y + "," + z + " " + cell.kind(), expectedCore, core.get(key));
                            }
                            assertEquals(x + "," + y + "," + z + " " + cell.kind(), expectedShell, shell.get(key));
                        }
                    }
                }
            }
            assertTrue(kinds.toString(), kinds.containsAll(List.of("AIR", "WATER", "SOLID", "SOLID_RESERVED", "OUTSIDE")));
            assertTrue(context.diagnostics().toString(), context.diagnostics().isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void anchorRangeMatchesIrisPlacementBand() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.CENOTE));
        try {
            BakedFeature baked = new SubterrainBaker(SubterrainTestPacks.context(data, DataVersion.V26_3), SEED)
                    .bake(feature(data, 0), "pools").orElseThrow();

            int baseDepth = (int) Math.ceil(Math.min(24, 8 * 0.45) * 0.5);
            assertEquals(-60 + 12 / 2 + 3 + baseDepth, baked.lowestAnchorY());
            assertEquals(40 - (12 - 12 / 2 + 3), baked.highestAnchorY());
            for (BakedRoom room : baked.rooms()) {
                int center = room.originY() + room.anchorY();
                assertTrue(center + " below " + baked.lowestAnchorY(), center >= baked.lowestAnchorY());
                assertTrue(center + " above " + baked.highestAnchorY(), center <= baked.highestAnchorY());
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void roomBiomePaintsFloorsWallsAndCeilings() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.ROOM_CENOTE));
        try {
            BakedFeature baked = new SubterrainBaker(SubterrainTestPacks.context(data, DataVersion.V26_3), SEED)
                    .bake(feature(data, 0), "grottos").orElseThrow();

            for (BakedRoom room : baked.rooms()) {
                Map<Long, VanillaBlockState> core = index(room.core());
                Set<VanillaBlockState> painted = new TreeSet<>((first, second) -> first.canonical().compareTo(second.canonical()));
                for (TemplateBlock block : room.shell().blocks()) {
                    painted.add(block.state());
                    if (block.state().equals(GRASS)) {
                        assertTrue(touchesRoom(core, block, room));
                    }
                }
                assertTrue(painted.toString(), painted.contains(GRASS));
                assertTrue(painted.toString(), painted.contains(STONE));
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void roomsBeyondStructureReachAreClipped() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.TUBE));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);

            BakedFeature baked = new SubterrainBaker(context, SEED).bake(feature(data, 0), "tubes").orElseThrow();

            for (BakedRoom room : baked.rooms()) {
                assertTrue(room.anchorX() <= SubterrainBaker.MAX_REACH && room.core().sizeX() - 1 - room.anchorX() <= SubterrainBaker.MAX_REACH);
                assertTrue(room.anchorZ() <= SubterrainBaker.MAX_REACH && room.core().sizeZ() - 1 - room.anchorZ() <= SubterrainBaker.MAX_REACH);
                assertTrue(Math.max(room.core().sizeX(), room.core().sizeZ()) == 2 * SubterrainBaker.MAX_REACH + 1);
                assertTrue(room.core().blocks().stream().anyMatch(block -> block.state().equals(LAVA)));
            }
            assertTrue(baked.reach() <= SubterrainBaker.MAX_REACH);
            List<ExportDiagnostic> bounds = SubterrainTestPacks.diagnostics(context.diagnostics(), ExportLimit.OBJECT_BOUNDS);
            assertEquals(1, bounds.size());
            assertEquals("tubes", bounds.getFirst().resource());
        } finally {
            data.close();
        }
    }

    @Test
    public void featureThatCannotFitIsNotBaked() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.SHALLOW));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);

            Optional<BakedFeature> baked = new SubterrainBaker(context, SEED).bake(feature(data, 0), "shallow");

            assertFalse(baked.isPresent());
            assertEquals(1, SubterrainTestPacks.diagnostics(context.diagnostics(), ExportLimit.STRUCTURE_PLACEMENT).size());
        } finally {
            data.close();
        }
    }

    @Test
    public void bakingIsDeterministic() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.CENOTE));
        try {
            IrisSubterrainFeature feature = feature(data, 0);

            BakedFeature first = new SubterrainBaker(SubterrainTestPacks.context(data, DataVersion.V26_3), SEED).bake(feature, "pools").orElseThrow();
            BakedFeature second = new SubterrainBaker(SubterrainTestPacks.context(data, DataVersion.V26_3), SEED).bake(feature, "pools").orElseThrow();

            assertEquals(first, second);
        } finally {
            data.close();
        }
    }

    private static IrisSubterrainFeature feature(IrisData data, int index) {
        IrisDimension dimension = data.getDimensionLoader().load(ExportTestPacks.DIMENSION, false);
        assertNotNull(dimension);
        return dimension.getSubterrainFeatures().get(index);
    }

    private static SubterrainPlan plan(SubterrainPlanner planner, BakedRoom room) {
        for (SubterrainPlan plan : planner.plansForBounds(room.originX(), room.originZ(),
                room.originX() + room.core().sizeX() - 1, room.originZ() + room.core().sizeZ() - 1)) {
            if (plan.bounds().minX() == room.originX() && plan.bounds().minY() == room.originY() && plan.bounds().minZ() == room.originZ()) {
                return plan;
            }
        }
        throw new AssertionError("No Iris plan starts at " + room.originX() + "," + room.originY() + "," + room.originZ());
    }

    private static Map<Long, VanillaBlockState> index(TemplateVolume volume) {
        Map<Long, VanillaBlockState> blocks = new HashMap<>();
        for (TemplateBlock block : volume.blocks()) {
            assertTrue(blocks.put(key(block.x(), block.y(), block.z()), block.state()) == null);
        }
        return blocks;
    }

    private static boolean touchesRoom(Map<Long, VanillaBlockState> core, TemplateBlock block, BakedRoom room) {
        int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] offset : offsets) {
            int x = block.x() + offset[0];
            int y = block.y() + offset[1];
            int z = block.z() + offset[2];
            if (x == room.anchorX() && y == room.anchorY() && z == room.anchorZ()) {
                return true;
            }
            VanillaBlockState neighbor = core.get(key(x, y, z));
            if (CAVE_AIR.equals(neighbor) || WATER.equals(neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 42) ^ ((long) y << 21) ^ z;
    }
}
