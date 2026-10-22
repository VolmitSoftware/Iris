package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisFloatingChildBiomes;
import art.arcane.iris.generation.decoration.IrisProceduralObjects;
import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.structure.object.FloatingObjectFootprint;
import art.arcane.iris.structure.object.IrisAxisRotationClamp;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectScale;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.structure.object.IrisObjectPlacementScaleInterpolator;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.Vector3i;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Answers;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class PlacementReachPackTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final int SPIN_PERIOD = 360;
    private static final int MAXIMUM_CHECKED_KERNELS = 4096;
    private static final String V2_HEADER = "Iris V2 IOB;";

    private File pack;
    private IrisData data;
    private IrisDimension dimension;

    @Before
    public void loadPack() throws IOException {
        String configured = System.getenv("IRIS_TEST_PACK");
        Assume.assumeTrue(configured != null && !configured.isBlank());
        pack = new File(configured).getAbsoluteFile();
        Assume.assumeTrue(new File(pack, "objects").isDirectory());
        File[] dimensions = new File(pack, "dimensions").listFiles((folder, name) -> name.endsWith(".json"));
        Assume.assumeTrue(dimensions != null && dimensions.length > 0);
        bindPlatform(Files.createTempDirectory("iris-placement-reach").toFile());
        data = IrisData.get(pack);
        String dimensionKey = System.getenv().getOrDefault("IRIS_TEST_PACK_DIMENSION",
                dimensions[0].getName().substring(0, dimensions[0].getName().length() - 5));
        dimension = data.getDimensionLoader().load(dimensionKey);
        assertTrue("dimension " + dimensionKey + " must load", dimension != null);
    }

    @After
    public void unbindPlatform() {
        IrisServices.remove(PreservationRegistry.class);
        IrisPlatforms.unbind();
    }

    @Test
    public void everyObjectFileStaysInsideItsReachUnderRightAngleRotations() throws IOException {
        List<IrisObjectPlacement> placements = List.of(
                new IrisObjectPlacement(),
                new IrisObjectPlacement().setRotation(rotation(new IrisAxisRotationClamp(true, false, 0D, 0D, 90D),
                        new IrisAxisRotationClamp(true, false, 0D, 0D, 90D), new IrisAxisRotationClamp())),
                new IrisObjectPlacement().setRotation(rotation(new IrisAxisRotationClamp(),
                        new IrisAxisRotationClamp(true, false, 0D, 0D, 90D), new IrisAxisRotationClamp(true, false, 0D, 0D, 90D))),
                new IrisObjectPlacement().setRotation(IrisObjectRotation.of(0D, 0D, 0D)),
                new IrisObjectPlacement().setRotation(rotation(new IrisAxisRotationClamp(),
                        new IrisAxisRotationClamp(true, false, 0D, 0D, 45D), new IrisAxisRotationClamp()))
                        .setTranslate(new IrisObjectTranslate().setX(7).setZ(-3)));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(new File(pack, "objects").toPath())) {
            files = walk.filter(path -> path.toString().endsWith(".iob")).sorted().toList();
        }
        assertTrue(!files.isEmpty());
        Statistics statistics = new Statistics();
        NativeBlockState marker = mock(NativeBlockState.class, withSettings().stubOnly());
        for (Path file : files) {
            IrisObject object = readPositions(file, marker);
            for (IrisObjectPlacement placement : placements) {
                verifyCentered(file.toString(), object, placement, PlacementReach.of(object, placement, 1D), statistics);
            }
        }
        System.out.println("PLACEMENT_REACH_FILES objects=" + files.size() + " " + statistics);
    }

    @Test
    public void everyPackPlacementStaysInsideItsReach() {
        Statistics statistics = new Statistics();
        Set<IrisObjectPlacement> placements = new LinkedHashSet<>();
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            placements.addAll(region.getObjects());
        }
        for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
            placements.addAll(biome.getObjects());
        }
        for (IrisObjectPlacement placement : placements) {
            for (String key : placement.getPlace()) {
                IrisObject object = data.getObjectLoader().load(key);
                if (object == null) {
                    continue;
                }
                PlacementReach reach = PlacementReach.of(object, placement, placement.getMaximumScale(dimension));
                forEachVariant(object, placement, 1D, variant -> verifyCentered(key, variant, placement, reach, statistics));
            }
        }
        for (IrisProceduralPlacement procedural : proceduralPlacements()) {
            KList<IrisObject> variants = procedural.getVariantObjects(data);
            if (variants == null) {
                continue;
            }
            IrisObjectPlacement placement = procedural.asPlacement();
            for (IrisObject variant : variants) {
                PlacementReach reach = PlacementReach.of(variant, placement, placement.getMaximumScale(dimension));
                forEachVariant(variant, placement, 1D,
                        scaled -> verifyCentered(variant.getLoadKey(), scaled, placement, reach, statistics));
            }
        }
        assertTrue(statistics.checked > 0);
        System.out.println("PLACEMENT_REACH_PLACEMENTS placements=" + placements.size() + " " + statistics);
    }

    @Test
    public void floatingPlacementsStayInsideTheirReach() {
        Statistics statistics = new Statistics();
        for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
            KList<IrisFloatingChildBiomes> entries = biome.getFloatingChildBiomes();
            if (entries == null) {
                continue;
            }
            for (IrisFloatingChildBiomes entry : entries) {
                double shrink = entry.hasObjectShrink() ? entry.getShrinkScale().getMaxScale() : 1D;
                IrisBiome target = entry.getRealBiome(biome, data);
                for (IrisObjectPlacement placement : listOrEmpty(entry.getFloatingObjects())) {
                    verifyFloating(placement, shrink, false, statistics);
                }
                List<IrisObjectPlacement> anchored = new ArrayList<>();
                anchored.addAll(listOrEmpty(entry.getExtraObjects()));
                anchored.addAll(listOrEmpty(entry.getTopObjectOverrides()));
                anchored.addAll(listOrEmpty(entry.getBottomObjectOverrides()));
                if (target != null) {
                    anchored.addAll(listOrEmpty(entry.resolveTopObjects(target)));
                    anchored.addAll(listOrEmpty(entry.resolveBottomObjects(target)));
                }
                for (IrisObjectPlacement placement : anchored) {
                    verifyFloating(placement, shrink, true, statistics);
                }
            }
        }
        System.out.println("PLACEMENT_REACH_FLOATING " + statistics);
    }

    @Test
    public void reportsTheMantleWindow() {
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getEngine()).thenReturn(engine);
        when(engineMantle.getData()).thenReturn(data);
        MantleObjectComponent objects = new MantleObjectComponent(engineMantle);
        MantleFloatingObjectComponent floating = new MantleFloatingObjectComponent(engineMantle);
        int objectRadius = objects.getRadius();
        int objectInput = objects.getInputRadius();
        int objectEager = objects.getEagerInputRadius(objectInput);
        int floatingRadius = floating.getRadius();
        int floatingPassRadius = chunkRadius(floatingRadius);
        int objectPassRadius = chunkRadius(floatingRadius);
        int terrainRadius = chunkRadius(floatingRadius + objectEager + 1);
        int writerRadius = 2 * chunkRadius(floatingRadius + objectInput + 1);
        System.out.println("PLACEMENT_REACH_WINDOW objectReach=" + objectRadius
                + " objectInput=" + objectInput
                + " objectEager=" + objectEager
                + " sourceChunkRadius=" + MantleObjectComponent.sourceChunkRadius(objectRadius)
                + " floatingReach=" + floatingRadius
                + " floatingPassRadius=" + floatingPassRadius
                + " objectPassRadius=" + objectPassRadius
                + " terrainRadius=" + terrainRadius
                + " terrainWindow=" + ((2 * terrainRadius) + 1) + "x" + ((2 * terrainRadius) + 1)
                + " writerRadius=" + writerRadius);
        assertTrue(objectRadius > 0);
    }

    private void verifyFloating(IrisObjectPlacement placement, double shrink, boolean anchored, Statistics statistics) {
        if (placement == null || placement.getPlace() == null) {
            return;
        }
        for (String key : placement.getPlace()) {
            IrisObject object = data.getObjectLoader().load(key);
            if (object == null) {
                continue;
            }
            PlacementReach.Extent extent = PlacementReach.Extent.of(object)
                    .scaled(placement.getMaximumScale(dimension)).scaled(shrink);
            if (!anchored) {
                PlacementReach reach = PlacementReach.of(extent, placement);
                forEachVariant(object, placement, shrink, variant -> verifyCentered(key, variant, placement, reach, statistics));
                continue;
            }
            PlacementReach reach = PlacementReach.anchored(extent, placement);
            forEachVariant(object, placement, shrink, variant -> verifyAnchored(key, variant, reach, statistics));
        }
    }

    private void verifyAnchored(String key, IrisObject variant, PlacementReach reach, Statistics statistics) {
        FloatingObjectFootprint footprint = FloatingObjectFootprint.compute(variant);
        List<IrisBlockVector> positions = positions(variant);
        List<IrisObjectRotation> rotations = List.of(IrisObjectRotation.of(0D, 0D, 0D),
                IrisObjectRotation.xFlip180WithY(0D), IrisObjectRotation.xFlip180WithY(90D),
                IrisObjectRotation.xFlip180WithY(180D), IrisObjectRotation.xFlip180WithY(270D));
        for (IrisObjectRotation rotation : rotations) {
            IrisBlockVector anchor = rotation.rotate(new IrisBlockVector(footprint.getTallestKx(), 0, footprint.getTallestKz()), 0, 0, 0);
            int actual = 0;
            for (IrisBlockVector position : positions) {
                IrisBlockVector rotated = rotation.rotate(position, 0, 0, 0);
                actual = Math.max(actual, Math.abs((int) Math.round(rotated.getX()) - anchor.getBlockX()));
                actual = Math.max(actual, Math.abs((int) Math.round(rotated.getZ()) - anchor.getBlockZ()));
            }
            record(key, actual, reach.horizontal(), statistics);
        }
    }

    private void verifyCentered(String key, IrisObject object, IrisObjectPlacement placement, PlacementReach reach,
                                Statistics statistics) {
        IrisObjectRotation rotation = placement.getRotation();
        IrisObjectTranslate translate = placement.getTranslate();
        boolean translating = translate != null && translate.canTranslate();
        int[] spinsX = spins(rotation, 0);
        int[] spinsY = spins(rotation, 1);
        int[] spinsZ = spins(rotation, 2);
        List<IrisBlockVector> positions = positions(object);
        long kernels = (long) spinsX.length * spinsY.length * spinsZ.length;
        if (kernels > MAXIMUM_CHECKED_KERNELS) {
            fail("rotation for " + key + " has " + kernels + " kernels");
        }
        for (int spinX : spinsX) {
            for (int spinY : spinsY) {
                for (int spinZ : spinsZ) {
                    IrisBlockVector offset = translating
                            ? rotation.rotate(new IrisBlockVector(translate.getX(), translate.getY(), translate.getZ()), spinX, spinY, spinZ)
                            : null;
                    int actual = 0;
                    for (IrisBlockVector position : positions) {
                        IrisBlockVector moved = rotation.rotate(position, spinX, spinY, spinZ).clone();
                        if (offset != null) {
                            moved.add(offset);
                        }
                        actual = Math.max(actual, Math.abs((int) Math.round(moved.getX())));
                        actual = Math.max(actual, Math.abs((int) Math.round(moved.getZ())));
                    }
                    record(key, actual, reach.horizontal(), statistics);
                }
            }
        }
    }

    private static IrisObject readPositions(Path file, NativeBlockState marker) throws IOException {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            IrisObject object = new IrisObject(input.readInt(), input.readInt(), input.readInt());
            input.mark(Short.BYTES + V2_HEADER.length());
            int headerLength = input.readUnsignedShort();
            boolean v2 = headerLength == V2_HEADER.length()
                    && V2_HEADER.equals(new String(input.readNBytes(headerLength), StandardCharsets.US_ASCII));
            if (!v2) {
                input.reset();
            }
            int paletteSize = v2 ? input.readShort() : 0;
            for (int index = 0; index < paletteSize; index++) {
                input.readUTF();
            }
            int blocks = input.readInt();
            for (int index = 0; index < blocks; index++) {
                IrisBlockVector position = new IrisBlockVector(input.readShort(), input.readShort(), input.readShort());
                if (v2) {
                    input.readShort();
                } else {
                    input.readUTF();
                }
                object.getBlocks().put(position, marker);
            }
            return object;
        }
    }

    private static void record(String key, int actual, int reach, Statistics statistics) {
        statistics.checked++;
        statistics.maximumActual = Math.max(statistics.maximumActual, actual);
        statistics.maximumReach = Math.max(statistics.maximumReach, reach);
        statistics.slack += reach - actual;
        if (actual > reach) {
            fail(key + " places a block " + actual + " blocks from its origin, beyond its reach " + reach);
        }
    }

    private static List<IrisBlockVector> positions(IrisObject object) {
        List<IrisBlockVector> positions = new ArrayList<>(object.getBlocks().size() + 8);
        object.getBlocks().forEach((position, state) -> positions.add(position));
        Vector3i center = object.getCenter();
        int[] xs = {-center.getBlockX(), object.getW() - center.getBlockX() - 1};
        int[] ys = {-center.getBlockY(), object.getH() - center.getBlockY() - 1};
        int[] zs = {-center.getBlockZ(), object.getD() - center.getBlockZ() - 1};
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    positions.add(new IrisBlockVector(x, y, z));
                }
            }
        }
        return positions;
    }

    private void forEachVariant(IrisObject object, IrisObjectPlacement placement, double shrink, Consumer<IrisObject> consumer) {
        IrisObjectScale scale = placement.getScale();
        IrisObjectPlacementScaleInterpolator interpolation = scale == null || scale.getInterpolation() == null
                ? IrisObjectPlacementScaleInterpolator.NONE : scale.getInterpolation();
        for (double factor : scales(placement)) {
            IrisObject variant = factor == 1D && (scale == null || !scale.shouldScale())
                    ? object
                    : scale == null ? IrisObjectScale.getFixed(object, factor) : object.scaled(factor, interpolation);
            consumer.accept(shrink < 1D ? variant.scaled(shrink, IrisObjectPlacementScaleInterpolator.NONE) : variant);
        }
    }

    private double[] scales(IrisObjectPlacement placement) {
        IrisObjectScale scale = placement.getScale();
        if (scale == null) {
            return new double[]{dimension.getAllObjectScaleFactor()};
        }
        if (!scale.shouldScale()) {
            return new double[]{1D};
        }
        if (scale.getSize() != 1D) {
            return new double[]{scale.getSize()};
        }
        if (scale.getMinimumScale() == scale.getMaximumScale()) {
            return new double[]{scale.getMinimumScale()};
        }
        int count = Math.max(1, Math.min(scale.getVariations(), 32));
        double[] scales = new double[count];
        for (int index = 0; index < count; index++) {
            scales[index] = scale.getMinimumScale() + (((scale.getMaximumScale() - scale.getMinimumScale()) / count) * index);
        }
        return scales;
    }

    private List<IrisProceduralPlacement> proceduralPlacements() {
        List<IrisProceduralPlacement> placements = new ArrayList<>();
        for (IrisRegion region : dimension.getAllRegions(() -> data)) {
            addProcedural(placements, region.getProceduralObjects());
        }
        for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
            addProcedural(placements, biome.getProceduralObjects());
        }
        return placements;
    }

    private static void addProcedural(List<IrisProceduralPlacement> placements, IrisProceduralObjects procedural) {
        if (procedural != null && !procedural.isEmpty()) {
            placements.addAll(procedural.getAllPlacements());
        }
    }

    private static int[] spins(IrisObjectRotation rotation, int axis) {
        if (rotation == null || !rotation.canRotate()) {
            return new int[]{0};
        }
        boolean rotates = switch (axis) {
            case 0 -> rotation.canRotateX();
            case 1 -> rotation.canRotateY();
            default -> rotation.canRotateZ();
        };
        if (!rotates) {
            return new int[]{0};
        }
        LongOpenHashSet angles = new LongOpenHashSet();
        IntArrayList spins = new IntArrayList();
        for (int spin = -SPIN_PERIOD + 1; spin < SPIN_PERIOD; spin++) {
            double angle = switch (axis) {
                case 0 -> rotation.getXRotation(spin);
                case 1 -> rotation.getYRotation(spin);
                default -> rotation.getZRotation(spin);
            };
            if (angles.add(Double.doubleToLongBits(angle))) {
                spins.add(spin);
            }
        }
        return spins.toIntArray();
    }

    private static <T> List<T> listOrEmpty(KList<T> list) {
        return list == null ? List.of() : list;
    }

    private static IrisObjectRotation rotation(IrisAxisRotationClamp x, IrisAxisRotationClamp y, IrisAxisRotationClamp z) {
        IrisObjectRotation rotation = new IrisObjectRotation();
        rotation.setEnabled(true);
        rotation.setXAxis(x);
        rotation.setYAxis(y);
        rotation.setZAxis(z);
        return rotation;
    }

    private static int chunkRadius(int blockRadius) {
        return blockRadius > 0 ? Math.ceilDiv(blockRadius, 16) : 0;
    }

    private static void bindPlatform(File dataFolder) {
        IrisPlatforms.unbind();
        Map<String, NativeBlockState> states = new ConcurrentHashMap<>();
        PlatformRegistries registries = mock(PlatformRegistries.class, withSettings().stubOnly());
        when(registries.block(anyString())).thenAnswer(invocation -> state(states, invocation.getArgument(0)));
        when(registries.blockOrNull(anyString())).thenAnswer(invocation -> state(states, invocation.getArgument(0)));
        when(registries.blockOrNull(anyString(), anyBoolean())).thenAnswer(invocation -> state(states, invocation.getArgument(0)));
        when(registries.air()).thenAnswer(invocation -> state(states, "minecraft:air"));
        IrisPlatform platform = mock(IrisPlatform.class, Answers.CALLS_REAL_METHODS);
        when(platform.dataFolder()).thenReturn(dataFolder);
        when(platform.dataFile(any(String[].class))).thenAnswer(invocation -> {
            Object[] path = invocation.getRawArguments();
            return new File(dataFolder, String.join(File.separator, (String[]) path[0]));
        });
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        IrisServices.register(PreservationRegistry.class, new IgnoredPreservation());
    }

    private static NativeBlockState state(Map<String, NativeBlockState> states, String key) {
        return states.computeIfAbsent(key, ignored -> {
            String material = materialOf(key);
            boolean air = material.endsWith(":air") || material.endsWith("cave_air") || material.endsWith("void_air");
            NativeBlockState state = mock(NativeBlockState.class, withSettings().stubOnly());
            when(state.key()).thenReturn(key);
            when(state.materialKey()).thenReturn(material);
            when(state.isAir()).thenReturn(air);
            when(state.isSolid()).thenReturn(!air);
            when(state.isOccluding()).thenReturn(!air);
            return state;
        });
    }

    private static String materialOf(String key) {
        String trimmed = key.trim().toLowerCase(Locale.ROOT);
        int properties = trimmed.indexOf('[');
        String head = properties < 0 ? trimmed : trimmed.substring(0, properties);
        return head.indexOf(':') < 0 ? "minecraft:" + head : head;
    }

    private static final class IgnoredPreservation implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }

    private static final class Statistics {
        private long checked;
        private long slack;
        private int maximumActual;
        private int maximumReach;

        @Override
        public String toString() {
            return "checked=" + checked + " maxActual=" + maximumActual + " maxReach=" + maximumReach
                    + " meanSlack=" + (checked == 0 ? 0D : (double) slack / checked);
        }
    }
}
