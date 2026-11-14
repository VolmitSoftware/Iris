package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.Objects;

/**
 * The aquifer of an exported dimension: dry rock with open water where the surface lies below the fluid height. Cave
 * fluid and lava are not exported, because the caves they fill are not.
 */
public final class AquiferCompiler {
    private static final double LEVEL_ROUNDING_MARGIN = 0.5;
    private static final int OPEN_WATER_DEPTH = 8;
    private static final int OPEN_WATER_HEADROOM = 8;
    private static final int OPEN_WATER_REACH = 32;
    private static final int OPEN_WATER_SHORE = 8;
    private static final int OPEN_WATER_ESTIMATE_SLACK = 40;
    private static final Field OPEN_WATER_LIFT = new Field.Const(2.0);
    private static final Field DRY = new Field.Const(-1.0);
    private static final Field INSIDE_BAND = new Field.Const(1.0);

    private final CaveInputs inputs;
    private final Field blockY;

    public AquiferCompiler(CaveInputs inputs) {
        this.inputs = Objects.requireNonNull(inputs);
        this.blockY = inputs.blockY();
    }

    public AquiferDef compile() {
        AquiferDef dry = AquiferDef.dry(inputs.aquiferSurface());
        return new AquiferDef(dry.barrier(), openWater(dry.floodedness()), dry.spread(), dry.lava(), dry.exclusion(), dry.surfaceLevel());
    }

    private Field openWater(Field caveFlood) {
        Field surface = inputs.surface();
        Field nearFluid = new Field.RangeIf(blockY, inputs.fluidHeight() - OPEN_WATER_REACH, inputs.fluidHeight() + OPEN_WATER_HEADROOM + 1.0, INSIDE_BAND, DRY);
        Field lowEstimate = new Field.Binary(Field.BinaryOp.SUB, new Field.Const(inputs.fluidHeight() + OPEN_WATER_ESTIMATE_SLACK), inputs.aquiferSurface());
        Field aboveFloor = new Field.Binary(Field.BinaryOp.ADD, new Field.Binary(Field.BinaryOp.SUB, blockY, surface), new Field.Const(OPEN_WATER_DEPTH));
        Field lowColumn = new Field.Binary(Field.BinaryOp.SUB, new Field.Const(inputs.fluidHeight() + OPEN_WATER_SHORE + LEVEL_ROUNDING_MARGIN), surface);
        Field lift = new Field.Gate(aboveFloor, new Field.Gate(lowColumn, OPEN_WATER_LIFT));
        return new Field.Binary(Field.BinaryOp.ADD, caveFlood, new Field.Gate(nearFluid, new Field.Gate(lowEstimate, lift)));
    }
}
