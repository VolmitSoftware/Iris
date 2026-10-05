package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.cave.CarvingMode;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.decoration.coral.IrisCoral;
import art.arcane.iris.generation.decoration.coral.IrisCoralForm;
import art.arcane.iris.generation.decoration.crystal.IrisCrystal;
import art.arcane.iris.generation.decoration.formation.IrisFormation;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.generation.subterrain.IrisSubterrainFluid;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainPlan;
import art.arcane.iris.generation.subterrain.SubterrainPlanner;
import art.arcane.iris.generation.subterrain.SubterrainPosition;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.BlockStateKey;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NaturalCaveProceduralPlacementTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final Map<String, NativeBlockState> states = new HashMap<>();

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        when(registries.blockOrNull(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        when(registries.blockOrNull(anyString(), eq(false))).thenAnswer(invocation -> state(invocation.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
        states.clear();
    }

    @Test
    public void roomProportionalFormationRestsOnActualFloorWithoutTranslation() {
        Fixture fixture = fixture(new FixtureOptions(IrisSubterrainFluid.WATER, 0));
        IrisFormation formation = new IrisFormation().setName("natural-vault").setRoomHeightFraction(0.3)
                .setHeightMin(90).setHeightMax(90).setBaseWidthMin(1).setBaseWidthMax(1)
                .setRoughness(0).setLean(0).setJitter(0).setCarvingSupport(CarvingMode.CARVING_ONLY);
        IrisObject object = variant(formation, fixture);
        assertTrue(object.getH() <= Math.floor(fixture.room().vaultHeight() * 0.3) + 2);
        assertTrue(object.getH() > 3);
        assertTrue(object.getLoadKey().endsWith(fixture.room().featureId()));
        assertFloorPlacement(object, formation.asPlacement().setRotation(IrisObjectRotation.of(0, 0, 0)), fixture);
    }

    @Test
    public void crystalBaseAndRotatedShardsRestAboveProtectedCenoteFloor() {
        for (double angle : List.of(0D, 30D)) {
            Fixture fixture = fixture(new FixtureOptions(IrisSubterrainFluid.WATER, 0));
            IrisCrystal crystal = new IrisCrystal().setName("natural-crystal").setVariants(1)
                    .setBaseRadius(1.5).setBaseNoise(0).setShardCountMin(3).setShardCountMax(3)
                    .setShardLengthMin(5).setShardLengthMax(5).setShardBaseRadius(0.5)
                    .setSpreadAngle(10).setJitter(0);
            IrisObject object = variant(crystal, fixture);
            assertTrue(object.getH() > 3);
            IrisObjectPlacement placement = crystal.asPlacement().setRotation(IrisObjectRotation.of(angle, 0, 0));
            assertFloorPlacement(object, placement, fixture);
        }
    }

    @Test
    public void submergedCoralUsesOwnedFluidHeadAboveGlobalWaterAndWaterlogsOnlyInWater() {
        for (IrisSubterrainFluid fluid : IrisSubterrainFluid.values()) {
            Fixture fixture = fixture(new FixtureOptions(fluid, 12));
            assertTrue(fixture.room().floorY() > fixture.placer().getFluidHeight(fixture.x(), fixture.z()));
            IrisCoral coral = coral();
            IrisObject object = variant(coral, fixture);
            assertFloorPlacement(object, coral.asPlacement().setRotation(IrisObjectRotation.of(0, 0, 0)), fixture);
            SubterrainCell.Kind expected = fluid == IrisSubterrainFluid.WATER ? SubterrainCell.Kind.WATER : SubterrainCell.Kind.LAVA;
            for (Map.Entry<SubterrainPosition, NativeBlockState> entry : fixture.writes().entrySet()) {
                SubterrainPosition position = entry.getKey();
                assertEquals(expected, fixture.planner().sample(position.x(), position.y(), position.z()).kind());
                assertEquals(entry.getValue().key(), fluid == IrisSubterrainFluid.WATER, entry.getValue().isWaterLogged());
            }
        }
    }

    @Test
    public void authoredDownwardTranslationStillRejectsWholeObjectAtProtectedFloor() {
        Fixture fixture = fixture(new FixtureOptions(IrisSubterrainFluid.WATER, 0));
        IrisCrystal crystal = new IrisCrystal().setVariants(1).setBaseRadius(1)
                .setShardCountMin(1).setShardCountMax(1).setShardLengthMin(4).setShardLengthMax(4)
                .setSpreadAngle(0).setJitter(0);
        IrisObject object = variant(crystal, fixture);
        IrisObjectPlacement placement = crystal.asPlacement().setRotation(IrisObjectRotation.of(0, 0, 0))
                .setTranslate(new IrisObjectTranslate().setY(-1));
        CaveObjectPlacementTransaction transaction = transaction(fixture);
        int firstAirY = MantleObjectComponent.proceduralFloorY(fixture.placer(), fixture.x(), fixture.floorY() + 3, fixture.z());
        int result = object.placeOnFloor(fixture.x(), firstAirY, fixture.z(), transaction, placement, new RNG(17L),
                (position, block) -> transaction.setData(position.getX(), position.getY(), position.getZ(), "natural-crystal@1"), fixture.data());
        assertTrue(result >= 0);
        assertEquals(CaveObjectPlacementTransaction.CommitResult.REJECTED_SUBTERRAIN, transaction.commit());
        assertTrue(fixture.writes().isEmpty());
        assertTrue(fixture.metadata().isEmpty());
        assertTrue(fixture.placer().get(fixture.x(), fixture.floorY(), fixture.z()).isSolid());
    }

    private void assertFloorPlacement(IrisObject object, IrisObjectPlacement placement, Fixture fixture) {
        assertFalse(placement.isBottom());
        assertEquals(0, placement.getTranslate().getY());
        CaveObjectPlacementTransaction transaction = transaction(fixture);
        int firstAirY = MantleObjectComponent.proceduralFloorY(fixture.placer(), fixture.x(), fixture.floorY() + 3, fixture.z());
        assertEquals(fixture.floorY() + 1, firstAirY);
        int result = object.placeOnFloor(fixture.x(), firstAirY, fixture.z(), transaction, placement, new RNG(17L),
                (position, block) -> transaction.setData(position.getX(), position.getY(), position.getZ(), "natural@1"), fixture.data());
        assertTrue(result >= 0);
        assertTrue(fixture.writes().isEmpty());
        assertTrue(fixture.metadata().isEmpty());
        assertEquals(CaveObjectPlacementTransaction.CommitResult.COMMITTED, transaction.commit());
        assertFalse(fixture.writes().isEmpty());
        int lowest = Integer.MAX_VALUE;
        for (SubterrainPosition position : fixture.writes().keySet()) {
            lowest = Math.min(lowest, position.y());
            SubterrainCell cell = fixture.planner().sample(position.x(), position.y(), position.z());
            assertTrue(cell.occupied());
            assertFalse(cell.room().reservedPassage());
            assertTrue(position.y() > cell.room().floorY());
            assertTrue(position.y() < cell.room().ceilingY());
        }
        assertEquals(fixture.floorY() + 1, lowest);
        boolean supported = false;
        for (SubterrainPosition position : fixture.writes().keySet()) {
            if (position.y() == lowest) {
                assertTrue(fixture.placer().get(position.x(), lowest - 1, position.z()).isSolid());
                assertFalse(fixture.writes().containsKey(new SubterrainPosition(position.x(), lowest - 1, position.z())));
                supported = true;
            }
        }
        assertTrue(supported);
        assertFalse(fixture.metadata().isEmpty());
        assertFalse(placement.isBottom());
    }

    private CaveObjectPlacementTransaction transaction(Fixture fixture) {
        return new CaveObjectPlacementTransaction(fixture.placer(), fixture.floorY() + 3, 6);
    }

    private IrisObject variant(IrisProceduralPlacement procedural, Fixture fixture) {
        IrisObject object = procedural.getVariantObject(fixture.data(), new RNG(819L), fixture.room());
        assertNotNull(object);
        assertFalse(object.getBlocks().isEmpty());
        return object;
    }

    private IrisCoral coral() {
        return new IrisCoral().setName("natural-coral").setVariants(1).setForm(IrisCoralForm.PILLAR)
                .setHeightMin(6).setHeightMax(6).setPillarRadius(1).setTipClusterRadius(0)
                .setBlock("minecraft:tube_coral[waterlogged=true]").setCarvingSupport(CarvingMode.CARVING_ONLY);
    }

    private Fixture fixture(FixtureOptions options) {
        IrisSubterrainFeature feature = new IrisSubterrainFeature().setId("natural-room").setProbability(1)
                .setRadius(32).setHeight(40).setFluid(options.fluid()).setFluidDepth(options.fluidDepth())
                .setPillarSpacing(0).setFormationFraction(0).setWorldYRange(new IrisRange(20, 120));
        SubterrainPlanner planner = new SubterrainPlanner(new SubterrainPlanner.Options(List.of(feature), 83L, 0, 256, (x, z) -> "test-region"));
        SubterrainPlan plan = planner.plansForBounds(0, 0, 512, 512).getFirst();
        int x = plan.centerX() + 8;
        int z = plan.centerZ() + 8;
        SubterrainRoom room = plan.sample(x, plan.centerY(), z).room();
        assertNotNull(room);
        int floorY = room.floorY();
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(256);
        when(engine.getMinHeight()).thenReturn(0);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(220);
        when(engine.getSubterrainCell(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                planner.sample(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        IrisData data = mock(IrisData.class);
        when(data.getEngine()).thenReturn(engine);
        Map<SubterrainPosition, NativeBlockState> writes = new HashMap<>();
        Map<SubterrainPosition, Object> metadata = new HashMap<>();
        IObjectPlacer placer = mock(IObjectPlacer.class);
        when(placer.getEngine()).thenReturn(engine);
        when(placer.getFluidHeight()).thenReturn(0);
        when(placer.getFluidHeight(anyInt(), anyInt())).thenReturn(0);
        when(placer.isUnderwater(anyInt(), anyInt())).thenReturn(options.fluidDepth() > 0);
        when(placer.get(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            SubterrainPosition position = new SubterrainPosition(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            NativeBlockState written = writes.get(position);
            if (written != null) {
                return written;
            }
            SubterrainCell cell = planner.sample(position.x(), position.y(), position.z());
            return state(cell.owned() ? cell.material() : "minecraft:stone");
        });
        when(placer.isCarved(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                planner.sample(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)).occupied());
        when(placer.isSolid(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                placer.get(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)).isSolid());
        when(placer.isSurfaceSolid(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                placer.isSolid(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
        doAnswer(invocation -> {
            writes.put(new SubterrainPosition(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)), invocation.getArgument(3));
            return null;
        }).when(placer).set(anyInt(), anyInt(), anyInt(), any());
        doAnswer(invocation -> {
            metadata.put(new SubterrainPosition(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)), invocation.getArgument(3));
            return null;
        }).when(placer).setData(anyInt(), anyInt(), anyInt(), any());
        return new Fixture(planner, placer, data, room, x, z, floorY, writes, metadata);
    }

    private NativeBlockState state(String key) {
        NativeBlockState cached = states.get(key);
        if (cached != null) {
            return cached;
        }
        NativeBlockState block = mock(NativeBlockState.class);
        int properties = key.indexOf('[');
        String material = properties < 0 ? key : key.substring(0, properties);
        boolean water = material.equals("minecraft:water");
        boolean lava = material.equals("minecraft:lava");
        boolean air = material.endsWith("air");
        when(block.key()).thenReturn(key);
        when(block.materialKey()).thenReturn(material);
        when(block.isSolid()).thenReturn(!air && !water && !lava);
        when(block.isOccluding()).thenReturn(!air && !water && !lava);
        when(block.isAir()).thenReturn(air);
        when(block.isFluid()).thenReturn(water || lava);
        when(block.isWater()).thenReturn(water);
        when(block.isAirOrFluid()).thenReturn(air || water || lava);
        when(block.isWaterLogged()).thenReturn(key.contains("waterlogged=true"));
        when(block.withProperty(anyString(), anyString())).thenAnswer(invocation ->
                state(BlockStateKey.withProperty(key, invocation.getArgument(0), invocation.getArgument(1))));
        states.put(key, block);
        return block;
    }

    private record FixtureOptions(IrisSubterrainFluid fluid, int fluidDepth) {
    }

    private record Fixture(SubterrainPlanner planner, IObjectPlacer placer, IrisData data, SubterrainRoom room,
                           int x, int z, int floorY, Map<SubterrainPosition, NativeBlockState> writes,
                           Map<SubterrainPosition, Object> metadata) {
    }
}
