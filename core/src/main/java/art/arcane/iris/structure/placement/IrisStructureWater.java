package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.stream.ProvisionalAnswerException;
import art.arcane.iris.generation.stream.ProvisionalSampling;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWater;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWaterColumn;

public final class IrisStructureWater implements StructureWater {
    private final IrisComplex complex;
    private final int offsetY;

    private IrisStructureWater(IrisComplex complex, int offsetY) {
        this.complex = complex;
        this.offsetY = offsetY;
    }

    public static StructureWater mantle(IrisComplex complex) {
        return complex == null ? StructureWater.DRY : new IrisStructureWater(complex, 0);
    }

    public static StructureWater world(Engine engine) {
        IrisComplex complex = engine.getComplex();
        return complex == null ? StructureWater.DRY : new IrisStructureWater(complex, engine.getMinHeight());
    }

    @Override
    public StructureWaterColumn column(int x, int z) {
        long revision = ProvisionalSampling.revision();
        WaterRole role = complex.getWaterRoleStream().get(x, z);
        StructureWaterColumn column = role.wet()
                ? new StructureWaterColumn(true, river(role),
                complex.getRoundedHeighteightStream().get(x, z) + offsetY,
                (int) Math.round(complex.getWaterHeadStream().getDouble(x, z)) + offsetY)
                : StructureWaterColumn.DRY;
        if (ProvisionalSampling.revision() != revision && !complex.isPlatformMainThread()) {
            throw new ProvisionalAnswerException("Structure water at " + x + "," + z
                    + " read a provisional answer; structures are only placed and carved around final water.");
        }
        return column;
    }

    static boolean river(WaterRole role) {
        return switch (role) {
            case CHANNEL, POND, CASCADE, WATERFALL -> true;
            case NONE, BANK, SHORE, POND_SHORE, DAM, MOUTH, INLET -> false;
        };
    }
}
