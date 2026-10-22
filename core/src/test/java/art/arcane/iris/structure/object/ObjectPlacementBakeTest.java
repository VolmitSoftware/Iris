package art.arcane.iris.structure.object;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.testsupport.KeyedBlockState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

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

    private static IrisObject object() {
        IrisObject object = new IrisObject(5, 5, 5);
        KeyedBlockState stone = new KeyedBlockState("minecraft:stone");
        object.getBlocks().put(new IrisBlockVector(-2, 0, 1), stone);
        object.getBlocks().put(new IrisBlockVector(0, -1, 2), stone);
        object.getBlocks().put(new IrisBlockVector(2, 2, -2), new KeyedBlockState("minecraft:oak_log[axis=y]"));
        return object;
    }
}
