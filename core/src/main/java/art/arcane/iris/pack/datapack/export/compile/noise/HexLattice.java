package art.arcane.iris.pack.datapack.export.compile.noise;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;

import java.util.Objects;

public final class HexLattice {
    public static final double COLUMN_SPACING = Math.sqrt(3.0D);
    public static final double ROW_SPACING = 3.0D;
    public static final double VALUE_FREQUENCY = 0.18D;
    private static final DataVersion TARGET = DataVersion.V26_3;
    private static final double RANGE_LIMIT = 1.0e6D;
    private static final String NAMESPACE_PREFIX = "cell/";

    private HexLattice() {
    }

    public static Centers centers(String instanceId, double hexSizeBlocks, Field warpX, Field warpZ) {
        String prefix = prefix(instanceId);
        Field x = CellularLibrary.cellCoordinate(prefix + "hex_x", Axis.X, hexSizeBlocks, warpX);
        Field z = CellularLibrary.cellCoordinate(prefix + "hex_z", Axis.Z, hexSizeBlocks, warpZ);
        Field firstX = new Field.Shared(prefix + "hex_c1x", snap(x, COLUMN_SPACING, 0.0D));
        Field firstZ = new Field.Shared(prefix + "hex_c1z", snap(z, ROW_SPACING, 0.0D));
        Field secondX = new Field.Shared(prefix + "hex_c2x", snap(x, COLUMN_SPACING, COLUMN_SPACING / 2.0D));
        Field secondZ = new Field.Shared(prefix + "hex_c2z", snap(z, ROW_SPACING, ROW_SPACING / 2.0D));
        Field difference = new Field.Shared(prefix + "hex_diff", new Field.Binary(Field.BinaryOp.SUB,
                squaredDistance(x, z, firstX, firstZ), squaredDistance(x, z, secondX, secondZ)));
        return new Centers(
                new Field.Shared(prefix + "hex_cx", new Field.RangeIf(difference, -RANGE_LIMIT, 0.0D, firstX, secondX)),
                new Field.Shared(prefix + "hex_cz", new Field.RangeIf(difference, -RANGE_LIMIT, 0.0D, firstZ, secondZ)));
    }

    public static Field centerValue(String instanceId, double hexSizeBlocks, Field warpX, Field warpZ, NoiseRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        Centers centers = centers(instanceId, hexSizeBlocks, warpX, warpZ);
        IrisSampleFit.Profile vanilla = IrisSampleFit.vanilla(TARGET);
        IrisSampleFit.Profile iris = IrisSampleFit.simplexSlice();
        double factor = VALUE_FREQUENCY * vanilla.correlationLength() / iris.correlationLength();
        String prefix = prefix(instanceId);
        Field noise = new Field.Noise(LeafNoise.register(registry), 0.0D, 0.0D,
                new Field.Binary(Field.BinaryOp.MUL, centers.x(), new Field.Const(factor)),
                new Field.Const(CellHashNoise.plane(prefix + "hex_value")),
                new Field.Binary(Field.BinaryOp.MUL, centers.z(), new Field.Const(factor)));
        return new Field.Shared(prefix + "hex_value", LeafNoise.toMarginal(noise, vanilla, iris));
    }

    private static String prefix(String instanceId) {
        return NAMESPACE_PREFIX + VanillaResourceIdentifier.requirePath(instanceId, "hex lattice instance id") + "/";
    }

    private static Field snap(Field coordinate, double spacing, double offset) {
        if (offset == 0.0D) {
            return new Field.Round(Field.RoundMode.ROUND, coordinate, new Field.Const(spacing));
        }
        Field shifted = new Field.Binary(Field.BinaryOp.ADD, coordinate, new Field.Const(-offset));
        Field snapped = new Field.Round(Field.RoundMode.ROUND, shifted, new Field.Const(spacing));
        return new Field.Binary(Field.BinaryOp.ADD, snapped, new Field.Const(offset));
    }

    private static Field squaredDistance(Field x, Field z, Field centerX, Field centerZ) {
        Field deltaX = new Field.Binary(Field.BinaryOp.SUB, x, centerX);
        Field deltaZ = new Field.Binary(Field.BinaryOp.SUB, z, centerZ);
        return new Field.Binary(Field.BinaryOp.ADD, new Field.Unary(Field.UnaryOp.SQUARE, deltaX), new Field.Unary(Field.UnaryOp.SQUARE, deltaZ));
    }

    public record Centers(Field x, Field z) {
        public Centers {
            Objects.requireNonNull(x, "x");
            Objects.requireNonNull(z, "z");
        }
    }
}
