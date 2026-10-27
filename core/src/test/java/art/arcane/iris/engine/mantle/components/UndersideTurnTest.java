package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.volmlib.util.math.IrisBlockVector;
import com.volmit.geometry.BlockPosition;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** {@link UndersideTurn} turns positions the way the Iris rotation it stands in for, {@link ObjectRotation#xFlip180WithY}, does. */
public class UndersideTurnTest {
    private static final int[] Y_ROTATIONS = {0, 90, 180, 270};
    private static final int SPAN = 3;

    @Test
    public void turnsEveryPositionLikeTheFlippedIrisRotation() {
        for (int yRotation : Y_ROTATIONS) {
            UndersideTurn turn = new UndersideTurn(yRotation);
            ObjectRotation rotation = ObjectRotation.xFlip180WithY(yRotation);
            for (int x = -SPAN; x <= SPAN; x++) {
                for (int y = -SPAN; y <= SPAN; y++) {
                    for (int z = -SPAN; z <= SPAN; z++) {
                        IrisBlockVector expected = rotation.rotate(new IrisBlockVector(x, y, z), 0, 0, 0);
                        BlockPosition actual = turn.turn(x, y, z);
                        String at = yRotation + "° at " + x + "," + y + "," + z;
                        assertEquals(at, expected.getBlockX(), actual.x());
                        assertEquals(at, expected.getBlockY(), actual.y());
                        assertEquals(at, expected.getBlockZ(), actual.z());
                    }
                }
            }
        }
    }
}
