package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.decoration.IrisStiltSettings;
import art.arcane.iris.generation.decoration.IrisVacuumSettings;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.structure.object.FloatingObjectFootprint;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.structure.object.IrisAxisRotationClamp;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectPlacementScaleInterpolator;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectScale;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class PlacementReachTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int ORIGIN_X = 1000;
    private static final int ORIGIN_Y = 90;
    private static final int ORIGIN_Z = -2000;
    private static final int GROUND_Y = 64;

    private final Map<String, NativeBlockState> states = new ConcurrentHashMap<>();
    private IrisData data;
    private IrisData objectData;
    private Engine engine;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class, withSettings().stubOnly());
        when(registries.block(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        when(registries.blockOrNull(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        when(registries.blockOrNull(anyString(), anyBoolean())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        when(registries.air()).thenAnswer(invocation -> state("minecraft:air"));
        IrisPlatform platform = mock(IrisPlatform.class, withSettings().stubOnly());
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);

        ProceduralStream<NativeBlockState> rock = mock(ProceduralStream.class, withSettings().stubOnly());
        IrisComplex complex = mock(IrisComplex.class, withSettings().stubOnly());
        when(complex.getRockStream()).thenReturn(rock);
        engine = mock(Engine.class, withSettings().stubOnly());
        when(engine.getHeight()).thenReturn(256);
        when(engine.getMinHeight()).thenReturn(0);
        when(engine.getComplex()).thenReturn(complex);
        data = mock(IrisData.class, withSettings().stubOnly());
        when(data.getEngine()).thenReturn(engine);
        objectData = mock(IrisData.class, withSettings().stubOnly());
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void halfExtentReachIsTheCenteredFrameNotTheFullWidth() {
        IrisObject object = singleBlockObject(103, 75, 73);
        IrisObjectPlacement placement = new IrisObjectPlacement();

        assertEquals(51, PlacementReach.of(object, placement, 1D).horizontal());
        assertEquals(54, PlacementReach.of(object, placement, 1D).input());
        assertEquals(51, PlacementReach.of(object, placement.setRotation(IrisObjectRotation.of(0D, 0D, 0D)), 1D).horizontal());
    }

    @Test
    public void arbitraryAngleRotationUsesTheRotatedFrame() {
        IrisObject object = singleBlockObject(21, 5, 21);
        IrisObjectPlacement placement = new IrisObjectPlacement().setRotation(rotation(new IrisAxisRotationClamp(),
                new IrisAxisRotationClamp(true, false, 0D, 0D, 45D), new IrisAxisRotationClamp()));

        assertEquals(14, PlacementReach.of(object, placement, 1D).horizontal());
    }

    @Test
    public void slopeRotationIsBoundedEvenWhenTheConfiguredRotationIsDisabled() {
        IrisObject object = singleBlockObject(21, 9, 21);
        IrisObjectPlacement placement = new IrisObjectPlacement().setRotation(IrisObjectRotation.of(0D, 0D, 0D));

        assertEquals(10, PlacementReach.of(object, placement, 1D).horizontal());
        assertEquals(15, PlacementReach.of(object, placement.setRotateTowardsSlope(true), 1D).horizontal());
    }

    @Test
    public void unionKeepsTheLargestReachAndTheLargestInput() {
        PlacementReach union = new PlacementReach(10, 2).union(new PlacementReach(4, 12));

        assertEquals(10, union.horizontal());
        assertEquals(16, union.input());
        assertEquals(19, union.withReadMargin(9).input());
        assertEquals(16, union.withReadMargin(1).input());
    }

    @Test
    public void scaledSpanBoundsEveryScaledVariant() {
        Random random = new Random(91L);
        double[] scales = {0.01D, 0.25D, 0.45D, 0.5D, 0.75D, 0.95D, 1D, 1.3D, 1.5D, 2D, 2.6D, 3.7D, 4D};
        for (int trial = 0; trial < 120; trial++) {
            int outside = trial % 3 == 0 ? 1 + random.nextInt(3) : 0;
            IrisObject object = randomObject(random, outside);
            PlacementReach.Extent extent = PlacementReach.Extent.of(object);
            for (double scale : scales) {
                for (IrisObjectPlacementScaleInterpolator interpolation : List.of(IrisObjectPlacementScaleInterpolator.NONE,
                        IrisObjectPlacementScaleInterpolator.TRILINEAR)) {
                    if (interpolation != IrisObjectPlacementScaleInterpolator.NONE && scale > 2D) {
                        continue;
                    }
                    IrisObject scaled = object.scaled(scale, interpolation);
                    for (double maximum : new double[]{scale, Math.min(IrisObjectScale.MAXIMUM_FACTOR, scale * 1.4D)}) {
                        assertContains(extent.scaled(maximum), scaled, "scale " + scale + " bounded at " + maximum);
                        for (double shrink : new double[]{0.45D, 0.8D}) {
                            assertContains(extent.scaled(maximum).scaled(shrink),
                                    scaled.scaled(shrink, IrisObjectPlacementScaleInterpolator.NONE),
                                    "scale " + scale + " shrink " + shrink);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void runnerWritesAndReadsStayInsideTheReach() {
        Random random = new Random(1337L);
        for (int trial = 0; trial < 260; trial++) {
            IrisObject object = randomObject(random, trial % 5 == 0 ? 2 : 0);
            object.setLoader(objectData);
            IrisObjectPlacement placement = randomPlacement(random);
            PlacementReach reach = PlacementReach.of(object, placement, placement.getMaximumScale(null));
            for (int seed = 0; seed < 12; seed++) {
                RNG rng = new RNG((trial * 7919L) + seed);
                IrisObject variant = placement.getScale() == null ? object : placement.getScale().get(rng, object);
                RecordingPlacer placer = new RecordingPlacer(engine);
                variant.place(ORIGIN_X, ORIGIN_Y, ORIGIN_Z, placer, placement, rng, null, null, data);
                String context = "trial " + trial + " seed " + seed + " mode " + placement.getMode()
                        + " rotation " + placement.getRotation() + " translate " + placement.getTranslate()
                        + " scale " + placement.getScale() + " warp " + placement.getWarp().getMultiplier();
                assertTrue(context + " writes " + placer.writeReach + " > " + reach.horizontal(),
                        placer.writeReach <= reach.horizontal());
                assertTrue(context + " reads " + placer.readReach + " > " + reach.input(),
                        placer.readReach <= reach.input());
            }
        }
    }

    @Test
    public void anchoredReachCoversTheFloatingFootprintAnchor() {
        Random random = new Random(7L);
        for (int trial = 0; trial < 80; trial++) {
            IrisObject object = randomObject(random, trial % 4 == 0 ? 1 : 0);
            PlacementReach reach = PlacementReach.anchored(PlacementReach.Extent.of(object), new IrisObjectPlacement());
            FloatingObjectFootprint footprint = FloatingObjectFootprint.compute(object);
            for (IrisObjectRotation rotation : List.of(IrisObjectRotation.of(0D, 0D, 0D),
                    IrisObjectRotation.xFlip180WithY(0D), IrisObjectRotation.xFlip180WithY(90D),
                    IrisObjectRotation.xFlip180WithY(180D), IrisObjectRotation.xFlip180WithY(270D))) {
                IrisBlockVector anchor = rotation.rotate(new IrisBlockVector(footprint.getTallestKx(), 0, footprint.getTallestKz()), 0, 0, 0);
                object.getBlocks().forEach((position, state) -> {
                    IrisBlockVector rotated = rotation.rotate(position, 0, 0, 0);
                    assertTrue(Math.abs((int) Math.round(rotated.getX()) - anchor.getBlockX()) <= reach.horizontal());
                    assertTrue(Math.abs((int) Math.round(rotated.getZ()) - anchor.getBlockZ()) <= reach.horizontal());
                });
            }
        }
    }

    private IrisObjectPlacement randomPlacement(Random random) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setRotation(randomRotation(random));
        if (random.nextInt(3) > 0) {
            placement.setTranslate(new IrisObjectTranslate()
                    .setX(random.nextInt(41) - 20)
                    .setY(random.nextInt(21) - 10)
                    .setZ(random.nextInt(41) - 20));
        }
        if (random.nextInt(3) == 0) {
            placement.setWarp(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).setMultiplier(1D + random.nextInt(9)));
        }
        List<ObjectPlaceMode> modes = List.of(ObjectPlaceMode.CENTER_HEIGHT, ObjectPlaceMode.STILT, ObjectPlaceMode.FAST_STILT,
                ObjectPlaceMode.CENTER_STILT, ObjectPlaceMode.ERODE_STILT, ObjectPlaceMode.ORGANIC_STILT, ObjectPlaceMode.CEILING_HANG,
                ObjectPlaceMode.VACUUM, ObjectPlaceMode.VACUUM_HIGH, ObjectPlaceMode.VACUUM_FAST, ObjectPlaceMode.VACUUM_ORGANIC,
                ObjectPlaceMode.VACUUM_WAVY, ObjectPlaceMode.STRUCTURE_PIECE);
        placement.setMode(modes.get(random.nextInt(modes.size())));
        if (random.nextInt(4) == 0) {
            placement.setVacuumSettings(new IrisVacuumSettings().setRadius(1 + random.nextInt(16)));
        }
        if (random.nextInt(4) == 0) {
            placement.setStiltSettings(new IrisStiltSettings());
        }
        placement.setBore(random.nextInt(4) == 0);
        placement.setBoreExtendMaxY(random.nextInt(3));
        placement.setSnow(random.nextInt(4) == 0 ? 0.6D : 0D);
        placement.setSurfaceSupportBuffer(random.nextInt(5));
        switch (random.nextInt(5)) {
            case 0 -> placement.setScale(new IrisObjectScale().setSize(0.3D + (random.nextDouble() * 1.6D)));
            case 1 -> placement.setScale(new IrisObjectScale().setMinimumScale(0.5D).setMaximumScale(2.3D)
                    .setVariations(5).setInterpolation(IrisObjectPlacementScaleInterpolator.TRILINEAR));
            case 2 -> placement.setScale(new IrisObjectScale().setMinimumScale(0.4D).setMaximumScale(0.9D).setVariations(3));
            default -> {
            }
        }
        return placement;
    }

    private static IrisObjectRotation randomRotation(Random random) {
        return switch (random.nextInt(9)) {
            case 0 -> new IrisObjectRotation();
            case 1 -> IrisObjectRotation.of(0D, 0D, 0D);
            case 2 -> rotation(new IrisAxisRotationClamp(), new IrisAxisRotationClamp(true, false, 0D, 0D, 45D), new IrisAxisRotationClamp());
            case 3 -> rotation(new IrisAxisRotationClamp(), new IrisAxisRotationClamp(true, false, 0D, 0D, 0D), new IrisAxisRotationClamp());
            case 4 -> IrisObjectRotation.of(90D, 0D, 0D);
            case 5 -> rotation(new IrisAxisRotationClamp(true, false, 0D, 0D, 90D),
                    new IrisAxisRotationClamp(true, false, 0D, 0D, 90D), new IrisAxisRotationClamp(true, false, 0D, 0D, 90D));
            case 6 -> rotation(new IrisAxisRotationClamp(true, false, 0D, 0D, 0D),
                    new IrisAxisRotationClamp(true, false, 0D, 0D, 0D), new IrisAxisRotationClamp(true, false, 0D, 0D, 30D));
            case 7 -> IrisObjectRotation.of(30D, 135D, 0D);
            default -> rotation(new IrisAxisRotationClamp(), new IrisAxisRotationClamp(true, false, 20D, 110D, 15D),
                    new IrisAxisRotationClamp(true, true, 180D, 180D, 0D));
        };
    }

    private IrisObject randomObject(Random random, int outside) {
        int width = 1 + random.nextInt(18);
        int height = 1 + random.nextInt(14);
        int depth = 1 + random.nextInt(18);
        IrisObject object = new IrisObject(width, height, depth);
        int count = 1 + random.nextInt(Math.max(1, (width * height * depth) / 3));
        for (int index = 0; index < count; index++) {
            int x = random.nextInt(width) - (width / 2);
            int y = random.nextInt(height) - (height / 2);
            int z = random.nextInt(depth) - (depth / 2);
            object.getBlocks().put(new IrisBlockVector(x, y, z), state(random.nextInt(5) == 0 ? "minecraft:oak_leaves" : "minecraft:stone"));
        }
        for (int index = 0; index < outside; index++) {
            int x = random.nextInt(width + 10) - ((width + 10) / 2);
            int y = random.nextInt(height + 6) - ((height + 6) / 2);
            int z = random.nextInt(depth + 10) - ((depth + 10) / 2);
            object.getBlocks().put(new IrisBlockVector(x, y, z), state("minecraft:stone"));
        }
        return object;
    }

    private IrisObject singleBlockObject(int width, int height, int depth) {
        IrisObject object = new IrisObject(width, height, depth);
        object.getBlocks().put(new IrisBlockVector(0, 0, 0), state("minecraft:stone"));
        return object;
    }

    private static void assertContains(PlacementReach.Extent extent, IrisObject object, String context) {
        object.getBlocks().forEach((position, state) -> {
            assertTrue(context + " x " + position.getBlockX() + " outside " + extent.x(),
                    position.getBlockX() >= extent.x().minimum() && position.getBlockX() <= extent.x().maximum());
            assertTrue(context + " y " + position.getBlockY() + " outside " + extent.y(),
                    position.getBlockY() >= extent.y().minimum() && position.getBlockY() <= extent.y().maximum());
            assertTrue(context + " z " + position.getBlockZ() + " outside " + extent.z(),
                    position.getBlockZ() >= extent.z().minimum() && position.getBlockZ() <= extent.z().maximum());
        });
        int centerX = object.getCenter().getBlockX();
        int centerZ = object.getCenter().getBlockZ();
        assertTrue(context + " frame x", -centerX >= extent.x().minimum() && object.getW() - 1 - centerX <= extent.x().maximum());
        assertTrue(context + " frame z", -centerZ >= extent.z().minimum() && object.getD() - 1 - centerZ <= extent.z().maximum());
    }

    private static IrisObjectRotation rotation(IrisAxisRotationClamp x, IrisAxisRotationClamp y, IrisAxisRotationClamp z) {
        IrisObjectRotation rotation = new IrisObjectRotation();
        rotation.setEnabled(true);
        rotation.setXAxis(x);
        rotation.setYAxis(y);
        rotation.setZAxis(z);
        return rotation;
    }

    private NativeBlockState state(String key) {
        return states.computeIfAbsent(key, ignored -> {
            String material = key.toLowerCase(Locale.ROOT);
            boolean air = material.endsWith("air");
            NativeBlockState state = mock(NativeBlockState.class, withSettings().stubOnly());
            when(state.key()).thenReturn(key);
            when(state.materialKey()).thenReturn(material);
            when(state.isAir()).thenReturn(air);
            when(state.isSolid()).thenReturn(!air);
            when(state.isOccluding()).thenReturn(!air);
            return state;
        });
    }

    private static final class RecordingPlacer implements IObjectPlacer {
        private final Engine engine;
        private int writeReach;
        private int readReach;

        private RecordingPlacer(Engine engine) {
            this.engine = engine;
        }

        private void write(int x, int z) {
            writeReach = Math.max(writeReach, Math.max(Math.abs(x - ORIGIN_X), Math.abs(z - ORIGIN_Z)));
            read(x, z);
        }

        private void read(int x, int z) {
            readReach = Math.max(readReach, Math.max(Math.abs(x - ORIGIN_X), Math.abs(z - ORIGIN_Z)));
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            read(x, z);
            return GROUND_Y;
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            read(x, z);
            return GROUND_Y;
        }

        @Override
        public void set(int x, int y, int z, NativeBlockState d) {
            write(x, z);
        }

        @Override
        public NativeBlockState get(int x, int y, int z) {
            read(x, z);
            return null;
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            read(x, z);
            return false;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            read(x, z);
            return y < GROUND_Y;
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            read(x, z);
            return false;
        }

        @Override
        public int getFluidHeight() {
            return GROUND_Y - 1;
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public void setTile(int xx, int yy, int zz, TileData tile) {
            write(xx, zz);
        }

        @Override
        public <T> void setData(int xx, int yy, int zz, T data) {
            write(xx, zz);
        }

        @Override
        public <T> T getData(int xx, int yy, int zz, Class<T> t) {
            read(xx, zz);
            return null;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }
    }
}
