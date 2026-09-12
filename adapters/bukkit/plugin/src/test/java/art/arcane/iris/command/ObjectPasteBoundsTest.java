package art.arcane.iris.command;

import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectRotation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ObjectPasteBoundsTest {
    private static final int ANCHOR_X = 100;
    private static final int ANCHOR_Y = 65;
    private static final int ANCHOR_Z = -30;

    @Test
    public void preservesPasteBoundsWithoutRotation() {
        ObjectPasteBounds bounds = resolve(0);

        assertEquals(new ObjectPasteBounds(98, 64, -31, 101, 66, -30), bounds);
    }

    @Test
    public void followsQuarterTurnPasteFootprint() {
        ObjectPasteBounds bounds = resolve(90);

        assertEquals(new ObjectPasteBounds(99, 64, -31, 100, 66, -28), bounds);
    }

    @Test
    public void followsHalfTurnPasteOffset() {
        ObjectPasteBounds bounds = resolve(180);

        assertEquals(new ObjectPasteBounds(99, 64, -30, 102, 66, -29), bounds);
    }

    @Test
    public void followsThreeQuarterTurnPasteOffset() {
        ObjectPasteBounds bounds = resolve(270);

        assertEquals(new ObjectPasteBounds(100, 64, -32, 101, 66, -29), bounds);
    }

    @Test
    public void enclosesRoundedArbitraryAnglePasteFootprint() {
        ObjectPasteBounds bounds = resolve(45);

        assertEquals(new ObjectPasteBounds(98, 64, -31, 101, 66, -29), bounds);
    }

    private ObjectPasteBounds resolve(int rotation) {
        IrisObject object = new IrisObject(4, 3, 2);
        return ObjectPasteBounds.resolve(object, IrisObjectRotation.of(0, rotation, 0), ANCHOR_X, ANCHOR_Y, ANCHOR_Z);
    }
}
