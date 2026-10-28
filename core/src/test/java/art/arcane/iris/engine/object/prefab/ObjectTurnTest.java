package art.arcane.iris.engine.object.prefab;

import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.engine.object.value.IrisDirection;
import art.arcane.volmlib.util.math.IrisBlockVector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The core object model turns what the built-in modules' placement rotation locked to the same angles turns. */
public class ObjectTurnTest {
    private static final double[] ANGLES = {0, 90, 180, 270, -90, -180, -270, 360, 45, 30, -45, 135};
    private static final int[] COORDINATES = {-3, 0, 2};

    @Test
    public void turnsPositionsLikeTheLockedPlacementRotation() {
        for (double x : ANGLES) {
            for (double y : ANGLES) {
                for (double z : ANGLES) {
                    ObjectTurn turn = ObjectTurn.of(x, y, z);
                    ObjectRotation rotation = ObjectRotation.of(x, y, z);
                    for (int px : COORDINATES) {
                        for (int py : COORDINATES) {
                            for (int pz : COORDINATES) {
                                IrisBlockVector expected = rotation.rotate(new IrisBlockVector(px, py, pz), 0, 0, 0);
                                IrisBlockVector actual = turn.rotate(new IrisBlockVector(px, py, pz));
                                String at = x + "," + y + "," + z + " of " + px + "," + py + "," + pz;
                                assertEquals(at, expected.getX(), actual.getX(), 0D);
                                assertEquals(at, expected.getY(), actual.getY(), 0D);
                                assertEquals(at, expected.getZ(), actual.getZ(), 0D);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void turnsDirectionsLikeTheLockedPlacementRotation() {
        for (double y : ANGLES) {
            for (IrisDirection direction : IrisDirection.values()) {
                assertEquals(y + " " + direction, ObjectRotation.of(0, y, 0).rotate(direction), ObjectTurn.of(0, y, 0).rotate(direction));
            }
        }
    }

    @Test
    public void aTurnOfNothingHandsBackThePositionItself() {
        IrisBlockVector position = new IrisBlockVector(1, 2, 3);

        assertFalse(ObjectTurn.NONE.canRotate());
        assertSame(position, ObjectTurn.NONE.rotate(position));
        assertTrue(ObjectTurn.of(0, 90, 0).canRotate());
    }

    @Test
    public void theBlockTurnTurnsDirections() {
        com.volmit.block.BlockTurn.Direction north = ObjectTurn.of(0, 90, 0).blockTurn().turn(0, 0, -1);

        assertEquals(-1D, north.x(), 0D);
        assertEquals(0D, north.y(), 0D);
        assertEquals(0D, north.z(), 0D);
    }
}
