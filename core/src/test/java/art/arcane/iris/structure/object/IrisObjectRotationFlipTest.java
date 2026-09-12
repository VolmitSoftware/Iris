package art.arcane.iris.structure.object;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IrisObjectRotationFlipTest {
    @Test
    public void xFlip180_canRotateX_returnsTrue() {
        IrisObjectRotation rot = IrisObjectRotation.xFlip180();
        assertTrue(rot.canRotateX());
    }

    @Test
    public void xFlip180_canRotateY_returnsFalse() {
        IrisObjectRotation rot = IrisObjectRotation.xFlip180();
        assertTrue(!rot.canRotateY());
    }

    @Test
    public void xFlip180RandomY_canRotateY_returnsTrue() {
        IrisObjectRotation rot = IrisObjectRotation.xFlip180RandomY();
        assertTrue(rot.canRotateY());
    }

    @Test
    public void xFlip180WithY_zeroYaw_stillUsesFixedYRotation() {
        IrisObjectRotation rot = IrisObjectRotation.xFlip180WithY(0);
        IrisBlockVector v = new IrisBlockVector(1, 2, 3);
        IrisBlockVector result = rot.rotate(v, 117, 253, 91);
        assertTrue(rot.canRotateY());
        assertEquals(1, result.getBlockX());
        assertEquals(-2, result.getBlockY());
        assertEquals(-3, result.getBlockZ());
    }

    @Test
    public void xFlip180WithY_ninetyYaw_rotatesMirroredFootprint() {
        IrisObjectRotation rot = IrisObjectRotation.xFlip180WithY(90);
        IrisBlockVector v = new IrisBlockVector(2, 5, 3);
        IrisBlockVector result = rot.rotate(v, 0, 0, 0);
        assertEquals(-3, result.getBlockX());
        assertEquals(-5, result.getBlockY());
        assertEquals(-2, result.getBlockZ());
    }

    @Test
    public void xFlip180_rotateVector_negatesYandZ() {
        IrisObjectRotation rot = IrisObjectRotation.xFlip180();
        IrisBlockVector v = new IrisBlockVector(1, 2, 3);
        IrisBlockVector result = rot.rotate(v, 0, 0, 0);
        assertEquals(1, result.getBlockX());
        assertEquals(-2, result.getBlockY());
        assertEquals(-3, result.getBlockZ());
    }

    @Test
    public void xFlip180_rotateNegativeVector_negatesYandZ() {
        IrisObjectRotation rot = IrisObjectRotation.xFlip180();
        IrisBlockVector v = new IrisBlockVector(-3, -5, -7);
        IrisBlockVector result = rot.rotate(v, 0, 0, 0);
        assertEquals(-3, result.getBlockX());
        assertEquals(5, result.getBlockY());
        assertEquals(7, result.getBlockZ());
    }
}
