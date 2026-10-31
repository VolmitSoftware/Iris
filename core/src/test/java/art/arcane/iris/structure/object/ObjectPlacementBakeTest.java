package art.arcane.iris.structure.object;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.object.BakedVariant;
import art.arcane.iris.pack.datapack.export.compile.object.ObjectSource;
import art.arcane.iris.pack.datapack.export.compile.object.ObjectVariantBaker;
import art.arcane.iris.pack.datapack.export.compile.object.TreeRoots;
import art.arcane.iris.pack.datapack.export.compile.object.VariantSpec;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ObjectPlacementBakeTest {
    @Test
    public void spinsResolvingToTheSameKernelShareOneBake() {
        IrisObject object = object();
        IrisObjectRotation rotation = new IrisObjectRotation();

        ObjectPlacementBake first = ObjectPlacementBake.of(object, new SpinKernel(rotation, 0, 10, 0), false, null);
        ObjectPlacementBake second = ObjectPlacementBake.of(object, new SpinKernel(rotation, 3, 80, 9), false, null);
        ObjectPlacementBake turned = ObjectPlacementBake.of(object, new SpinKernel(rotation, 0, 100, 0), false, null);

        assertSame(first, second);
        assertNotSame(first, turned);
        assertEquals(3, first.count());
    }

    @Test
    public void bakedOffsetsMatchTheKernelTransformInCursorOrder() {
        IrisObject object = object();
        IrisObjectRotation rotation = IrisObjectRotation.of(0D, 90D, 0D);
        SpinKernel spin = new SpinKernel(rotation, 0, 0, 0);
        IrisBlockVector translate = new IrisBlockVector(1.5D, -2D, 0.25D);

        ObjectPlacementBake bake = ObjectPlacementBake.of(object, spin, true, translate);
        double[] exact = bake.exact(object, spin);

        int index = 0;
        for (IrisBlockVector source : object.getBlocks().keys()) {
            IrisBlockVector expected = source.clone();
            spin.rotate(expected);
            expected.setY(-expected.getBlockY());
            expected.add(translate);
            assertEquals((int) Math.round(expected.getX()), bake.offsetX(index));
            assertEquals((int) Math.round(expected.getY()), bake.offsetY(index));
            assertEquals((int) Math.round(expected.getZ()), bake.offsetZ(index));
            assertEquals(expected.getBlockY(), bake.layerY(index));
            assertEquals(expected.getX(), exact[index * 3], 0D);
            assertEquals(expected.getY(), exact[index * 3 + 1], 0D);
            assertEquals(expected.getZ(), exact[index * 3 + 2], 0D);
            index++;
        }
        assertEquals(bake.count(), index);
    }

    @Test
    public void editedObjectsRebake() {
        IrisObject object = object();
        SpinKernel spin = new SpinKernel(new IrisObjectRotation(), 0, 0, 0);
        ObjectPlacementBake before = ObjectPlacementBake.of(object, spin, false, null);

        object.getBlocks().put(new IrisBlockVector(1, 1, 1), new KeyedBlockState("minecraft:dirt"));
        ObjectPlacementBake after = ObjectPlacementBake.of(object, spin, false, null);

        assertNotSame(before, after);
        assertEquals(4, after.count());
        assertSame(after, ObjectPlacementBake.of(object, spin, false, null));
    }

    @Test
    public void highVariantPlacementsKeepCachedVariantsAndNeverRebuildTheLayout() {
        IrisObject object = object();
        IrisObjectRotation pine = new IrisObjectRotation();
        pine.setEnabled(true);
        pine.setXAxis(new IrisAxisRotationClamp(true, false, -9D, 9D, 1D));
        pine.setYAxis(new IrisAxisRotationClamp(true, false, 0D, 270D, 90D));
        pine.setZAxis(new IrisAxisRotationClamp(true, false, -9D, 9D, 1D));
        IrisBlockVector translate = new IrisBlockVector(0D, -6D, 0D);
        Random random = new Random(1444L);
        ObjectPlacementBake first = ObjectPlacementBake.of(object, new SpinKernel(pine, 0, 0, 0), false, translate);
        Map<ObjectPlacementBake.Key, ObjectPlacementBake> settled = null;
        Set<ObjectPlacementBake.Key> distinct = new HashSet<>();
        for (int placement = 0; placement < 4000; placement++) {
            SpinKernel spin = new SpinKernel(pine, random.nextInt(360), random.nextInt(360), random.nextInt(360));
            ObjectPlacementBake bake = ObjectPlacementBake.of(object, spin, false, translate);
            distinct.add(ObjectPlacementBake.Key.of(spin, false, translate));
            assertSame(first.palette(), bake.palette());
            assertTrue(object.placementBakes.size() <= 32);
            if (spin.axisAligned) {
                assertTransformed(object, spin, false, translate, bake);
            } else {
                assertResampled(object, spin, translate, bake);
            }
            if (placement == 2000) {
                settled = new HashMap<>(object.placementBakes);
            }
        }
        assertTrue(distinct.size() > 40);
        assertEquals(32, settled.size());
        assertEquals(settled.keySet(), object.placementBakes.keySet());
        for (Map.Entry<ObjectPlacementBake.Key, ObjectPlacementBake> entry : settled.entrySet()) {
            assertSame(entry.getValue(), object.placementBakes.get(entry.getKey()));
        }
    }

    @Test
    public void diagonalYawTurnsFillEverySolidInteriorCellExactlyOnce() {
        IrisObject disc = leafDisc(6, 3);
        IrisObject box = leafBox(4, 3);
        IrisObjectRotation diagonal = yawRotation(45D);
        for (int degrees : new int[]{45, 135}) {
            SpinKernel spin = new SpinKernel(diagonal, 0, degrees, 0);
            assertSolidCoverage(ObjectPlacementBake.of(disc, spin, false, null), degrees, 6, 3,
                    (x, z) -> x * x + z * z <= 25D);
            assertSolidCoverage(ObjectPlacementBake.of(box, spin, false, null), degrees, 6, 3,
                    (x, z) -> Math.abs(x) <= 3.75D && Math.abs(z) <= 3.75D);
        }
    }

    @Test
    public void diagonalYawTurnsKeepEveryThinTrunkColumn() {
        IrisObject object = new IrisObject(7, 6, 7);
        KeyedBlockState log = new KeyedBlockState("minecraft:oak_log[axis=y]");
        int[][] columns = {{2, 0}, {0, 2}, {-2, 0}, {3, 1}};
        for (int[] column : columns) {
            for (int y = 0; y < 6; y++) {
                object.getBlocks().put(new IrisBlockVector(column[0], y, column[1]), log);
            }
        }
        IrisObjectRotation diagonal = yawRotation(45D);
        for (int degrees : new int[]{45, 135, 225, 315}) {
            SpinKernel spin = new SpinKernel(diagonal, 0, degrees, 0);
            ObjectPlacementBake bake = ObjectPlacementBake.of(object, spin, false, null);
            Set<Long> covered = new HashSet<>();
            Set<Long> sampled = new HashSet<>();
            for (int index = 0; index < bake.count(); index++) {
                assertTrue(covered.add(cell(bake.offsetX(index), bake.offsetY(index), bake.offsetZ(index))));
                IrisBlockVector inverse = new IrisBlockVector(bake.offsetX(index), bake.offsetY(index), bake.offsetZ(index));
                spin.inverseRotate(inverse);
                sampled.add(cell((int) Math.round(inverse.getX()), (int) Math.round(inverse.getY()), (int) Math.round(inverse.getZ())));
            }
            for (IrisBlockVector source : object.getBlocks().keys()) {
                IrisBlockVector forward = source.clone();
                spin.rotate(forward);
                boolean represented = sampled.contains(cell(source.getBlockX(), source.getBlockY(), source.getBlockZ()))
                        || covered.contains(cell((int) Math.round(forward.getX()), (int) Math.round(forward.getY()),
                        (int) Math.round(forward.getZ())));
                assertTrue(degrees + " degrees dropped trunk block " + source, represented);
            }
        }
    }

    @Test
    public void quarterTurnsKeepTheForwardTransform() {
        IrisObject object = leafDisc(6, 3);
        object.getBlocks().put(new IrisBlockVector(7, 1, -2), new KeyedBlockState("minecraft:oak_log[axis=x]"));
        IrisBlockVector translate = new IrisBlockVector(1.5D, -2D, 0.25D);
        IrisObjectRotation[] rotations = {new IrisObjectRotation(), yawRotation(45D)};
        for (IrisObjectRotation rotation : rotations) {
            for (int degrees = 0; degrees < 360; degrees += 90) {
                SpinKernel spin = new SpinKernel(rotation, 0, degrees, 0);
                assertTransformed(object, spin, false, null, ObjectPlacementBake.of(object, spin, false, null));
                assertTransformed(object, spin, true, translate, ObjectPlacementBake.of(object, spin, true, translate));
            }
        }
        for (int degrees = 90; degrees < 360; degrees += 90) {
            SpinKernel spin = new SpinKernel(IrisObjectRotation.of(0D, degrees, 0D), 0, 0, 0);
            assertTransformed(object, spin, false, null, ObjectPlacementBake.of(object, spin, false, null));
        }
    }

    @Test
    public void datapackVariantsMatchPlacementAtNonRightAngles() {
        IrisObject object = new IrisObject(13, 5, 13);
        KeyedBlockState leaves = new KeyedBlockState("minecraft:oak_leaves[distance=1,persistent=true]");
        for (int y = 0; y <= 2; y++) {
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    if (x * x + z * z <= 36) {
                        object.getBlocks().put(new IrisBlockVector(x, y, z), leaves);
                    }
                }
            }
        }
        for (int y = -2; y <= 1; y++) {
            object.getBlocks().put(new IrisBlockVector(0, y, 0), new KeyedBlockState("minecraft:oak_log[axis=y]"));
            object.getBlocks().put(new IrisBlockVector(4, y, -3), new KeyedBlockState("minecraft:birch_log[axis=y]"));
        }
        object.getBlocks().put(new IrisBlockVector(-5, 2, 2), new KeyedBlockState("minecraft:gold_block"));
        object.getBlocks().put(new IrisBlockVector(2, -2, 5), new KeyedBlockState("minecraft:stone"));
        List<TemplateBlock> blocks = new ArrayList<>();
        for (IrisBlockVector key : object.getBlocks().keys()) {
            blocks.add(new TemplateBlock(key.getBlockX() + object.getCenter().getX(), key.getBlockY() + object.getCenter().getY(),
                    key.getBlockZ() + object.getCenter().getZ(), VanillaBlockState.parse(object.getBlocks().get(key).key()), null));
        }
        ObjectSource source = new ObjectSource(new TemplateVolume(object.getW(), object.getH(), object.getD(), blocks), 0,
                TreeRoots.NONE);
        double[][] angles = {{0D, 45D, 0D}, {0D, 135D, 0D}, {0D, 30D, 0D}, {20D, 45D, 0D}, {0D, 60D, 15D}};
        for (double[] angle : angles) {
            IrisObjectRotation rotation = IrisObjectRotation.of(angle[0], angle[1], angle[2]);
            SpinKernel spin = new SpinKernel(rotation, 0, 0, 0);
            ObjectPlacementBake bake = ObjectPlacementBake.of(object, spin, false, null);
            int lift = rotation.rotate(new IrisBlockVector(0, object.getCenter().getY(), 0), 0, 0, 0).getBlockY();
            Set<String> placed = new HashSet<>();
            for (int index = 0; index < bake.count(); index++) {
                String key = bake.palette()[bake.paletteIndex(index)].key();
                placed.add(bake.offsetX(index) + "," + (bake.offsetY(index) + lift) + "," + bake.offsetZ(index) + " "
                        + key.substring(0, key.indexOf('[') < 0 ? key.length() : key.indexOf('[')));
            }
            BakedVariant variant = new ObjectVariantBaker().bake("iris_export:object/parity/a", source,
                    VariantSpec.builder().rotation(new VariantSpec.Rotation(angle[0], angle[1], angle[2])).build());
            Set<String> exported = new HashSet<>();
            TemplateVolume volume = variant.volume();
            for (TemplateBlock block : volume.blocks()) {
                exported.add((block.x() - (volume.sizeX() - 1) / 2) + "," + (block.y() + variant.baseY()) + ","
                        + (block.z() - (volume.sizeZ() - 1) / 2) + " " + block.state().name());
            }
            assertEquals(Arrays.toString(angle), placed, exported);
        }
    }

    private static void assertResampled(IrisObject object, SpinKernel spin, IrisBlockVector translate, ObjectPlacementBake bake) {
        double[] exact = bake.exact(object, spin);
        Set<Long> forward = new HashSet<>();
        for (IrisBlockVector source : object.getBlocks().keys()) {
            IrisBlockVector moved = source.clone();
            spin.rotate(moved);
            moved.add(translate);
            forward.add(cell((int) Math.round(moved.getX()), (int) Math.round(moved.getY()), (int) Math.round(moved.getZ())));
        }
        Set<Long> covered = new HashSet<>();
        Set<Long> sampled = new HashSet<>();
        for (int index = 0; index < bake.count(); index++) {
            long target = cell(bake.offsetX(index), bake.offsetY(index), bake.offsetZ(index));
            assertTrue(covered.add(target));
            IrisBlockVector inverse = new IrisBlockVector(bake.offsetX(index), bake.offsetY(index), bake.offsetZ(index));
            inverse.subtract(translate);
            spin.inverseRotate(inverse);
            IrisBlockVector nearest = new IrisBlockVector((int) Math.round(inverse.getX()), (int) Math.round(inverse.getY()),
                    (int) Math.round(inverse.getZ()));
            NativeBlockState state = bake.palette()[bake.paletteIndex(index)];
            NativeBlockState nearestState = object.getBlocks().get(nearest);
            if (nearestState != null) {
                assertSame(nearestState, state);
                sampled.add(cell(nearest.getBlockX(), nearest.getBlockY(), nearest.getBlockZ()));
            } else {
                assertTrue(forward.contains(target));
            }
            assertEquals(bake.offsetY(index), bake.layerY(index));
            assertEquals(bake.offsetX(index), exact[index * 3], 0D);
            assertEquals(bake.offsetY(index), exact[index * 3 + 1], 0D);
            assertEquals(bake.offsetZ(index), exact[index * 3 + 2], 0D);
        }
        for (IrisBlockVector source : object.getBlocks().keys()) {
            IrisBlockVector moved = source.clone();
            spin.rotate(moved);
            moved.add(translate);
            assertTrue(sampled.contains(cell(source.getBlockX(), source.getBlockY(), source.getBlockZ()))
                    || covered.contains(cell((int) Math.round(moved.getX()), (int) Math.round(moved.getY()),
                    (int) Math.round(moved.getZ()))));
        }
    }

    private static void assertSolidCoverage(ObjectPlacementBake bake, int degrees, int radius, int height, Interior interior) {
        Map<Long, Integer> covered = new HashMap<>();
        for (int index = 0; index < bake.count(); index++) {
            covered.merge(cell(bake.offsetX(index), bake.offsetY(index), bake.offsetZ(index)), 1, Integer::sum);
        }
        double cos = Math.cos(Math.toRadians(degrees));
        double sin = Math.sin(Math.toRadians(degrees));
        int reach = radius * 2;
        for (int y = 0; y < height; y++) {
            for (int x = -reach; x <= reach; x++) {
                for (int z = -reach; z <= reach; z++) {
                    int writes = covered.getOrDefault(cell(x, y, z), 0);
                    assertTrue(degrees + " degrees wrote cell " + x + "," + y + "," + z + " " + writes + " times", writes <= 1);
                    if (interior.contains(cos * x - sin * z, sin * x + cos * z)) {
                        assertEquals(degrees + " degrees left a hole at " + x + "," + y + "," + z, 1, writes);
                    }
                }
            }
        }
    }

    private static long cell(int x, int y, int z) {
        return (((long) x & 0x1FFFFFL) << 42) | (((long) y & 0x1FFFFFL) << 21) | ((long) z & 0x1FFFFFL);
    }

    private static IrisObjectRotation yawRotation(double interval) {
        IrisObjectRotation rotation = new IrisObjectRotation();
        rotation.setYAxis(new IrisAxisRotationClamp(true, false, 0D, 0D, interval));
        return rotation;
    }

    private static IrisObject leafDisc(int radius, int height) {
        IrisObject object = new IrisObject(radius * 2 + 1, height, radius * 2 + 1);
        KeyedBlockState leaves = new KeyedBlockState("minecraft:oak_leaves[distance=1,persistent=true]");
        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z <= radius * radius) {
                        object.getBlocks().put(new IrisBlockVector(x, y, z), leaves);
                    }
                }
            }
        }
        return object;
    }

    private static IrisObject leafBox(int half, int height) {
        IrisObject object = new IrisObject(half * 2 + 1, height, half * 2 + 1);
        KeyedBlockState leaves = new KeyedBlockState("minecraft:oak_leaves[distance=1,persistent=true]");
        for (int y = 0; y < height; y++) {
            for (int x = -half; x <= half; x++) {
                for (int z = -half; z <= half; z++) {
                    object.getBlocks().put(new IrisBlockVector(x, y, z), leaves);
                }
            }
        }
        return object;
    }

    private interface Interior {
        boolean contains(double x, double z);
    }

    private static void assertTransformed(IrisObject object, SpinKernel spin, boolean ceilingHang, IrisBlockVector translate,
                                          ObjectPlacementBake bake) {
        double[] exact = bake.exact(object, spin);
        int index = 0;
        for (IrisBlockVector source : object.getBlocks().keys()) {
            IrisBlockVector expected = source.clone();
            spin.rotate(expected);
            if (ceilingHang) {
                expected.setY(-expected.getBlockY());
            }
            if (translate != null) {
                expected.add(translate);
            }
            assertEquals((int) Math.round(expected.getX()), bake.offsetX(index));
            assertEquals((int) Math.round(expected.getY()), bake.offsetY(index));
            assertEquals((int) Math.round(expected.getZ()), bake.offsetZ(index));
            assertEquals(expected.getBlockY(), bake.layerY(index));
            assertEquals(expected.getX(), exact[index * 3], 0D);
            assertEquals(expected.getY(), exact[index * 3 + 1], 0D);
            assertEquals(expected.getZ(), exact[index * 3 + 2], 0D);
            index++;
        }
        assertEquals(bake.count(), index);
    }

    private static IrisObject object() {
        IrisObject object = new IrisObject(5, 5, 5);
        KeyedBlockState stone = new KeyedBlockState("minecraft:stone");
        object.getBlocks().put(new IrisBlockVector(-2, 0, 1), stone);
        object.getBlocks().put(new IrisBlockVector(0, -1, 2), stone);
        object.getBlocks().put(new IrisBlockVector(2, 2, -2), new KeyedBlockState("minecraft:oak_log[axis=y]"));
        return object;
    }
}
