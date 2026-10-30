package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HexLatticeTest {
    private static final String NAMESPACE = "iris_export";

    @Test
    public void centersSnapToTwoRectangularLattices() {
        HexLattice.Centers centers = HexLattice.centers("test/hex", 32.0D, null, null);
        FieldValidator.requireTwoDimensional(centers.x(), "hex x");
        FieldValidator.requireTwoDimensional(centers.z(), "hex z");
        List<Field.Round> rounds = FieldWalk.nodesOf(centers.x(), Field.Round.class);
        assertEquals(4, rounds.size());
        int columns = 0;
        int rows = 0;
        for (Field.Round round : rounds) {
            assertEquals(Field.RoundMode.ROUND, round.mode());
            if (round.multiple().equals(new Field.Const(HexLattice.COLUMN_SPACING))) {
                columns++;
            } else if (round.multiple().equals(new Field.Const(HexLattice.ROW_SPACING))) {
                rows++;
            }
        }
        assertEquals(2, columns);
        assertEquals(2, rows);
        Field.RangeIf pick = (Field.RangeIf) ((Field.Shared) centers.x()).expr();
        assertEquals(0.0D, pick.max(), 0.0D);
        FieldWalk.assertConstantsWithinLimit(centers.x());
        FieldWalk.assertConstantsWithinLimit(centers.z());
    }

    @Test
    public void valueSamplesRegisteredLeafAtCentre() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        Field value = HexLattice.centerValue("test/hex", 32.0D, null, null, registry);
        FieldValidator.requireTwoDimensional(value, "hex value");
        FieldWalk.assertNoisesRegistered(value, registry);
        FieldWalk.assertConstantsWithinLimit(value);
        List<Field.Noise> noises = FieldWalk.nodesOf(value, Field.Noise.class);
        assertEquals(1, noises.size());
        Field.Noise leaf = noises.getFirst();
        assertEquals(0.0D, leaf.xzScale(), 0.0D);
        assertEquals(0.0D, leaf.yScale(), 0.0D);
        assertTrue(leaf.shiftY() instanceof Field.Const);
        HexLattice.Centers centers = HexLattice.centers("test/hex", 32.0D, null, null);
        assertEquals(centers.x(), ((Field.Binary) leaf.shiftX()).left());
        assertEquals(centers.z(), ((Field.Binary) leaf.shiftZ()).left());
    }

    @Test
    public void latticeIsDeterministic() {
        NoiseRegistry registry = new NoiseRegistry(NAMESPACE);
        assertEquals(HexLattice.centerValue("test/hex", 48.0D, null, null, registry), HexLattice.centerValue("test/hex", 48.0D, null, null, registry));
        FieldWalk.sharedBodies(List.of(HexLattice.centerValue("test/hex", 48.0D, null, null, registry), HexLattice.centers("test/hex", 48.0D, null, null).x()));
    }
}
