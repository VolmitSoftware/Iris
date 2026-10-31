package art.arcane.iris.pack.datapack.export.compile;

import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;

public final class RouterCompiler {
    private static final Field DENSITY_PER_BLOCK = new Field.Const(1.0);
    private static final Field UNUSED_CLIMATE = new Field.Const(0.0);
    private static final double HALF_BLOCK = 0.5;
    private static final int VOLUME_CELL_XZ = 4;
    private static final int VOLUME_CELL_Y = 8;
    private static final int SURFACE_CELL_XZ = 16;
    private static final int SURFACE_CELL_Y = 1;

    private RouterCompiler() {
    }

    public static RouterDef compile(Field height, Field volumetric, Field caves, AquiferDef aquifer, BiomeCodes codes, DimensionDef dimension) {
        FieldValidator.requireTwoDimensional(height, "height");
        Field ramp = new Field.Binary(Field.BinaryOp.MUL, DENSITY_PER_BLOCK, new Field.Binary(Field.BinaryOp.SUB, height, blockCenterY(dimension)));
        Field terrain = new Field.Binary(Field.BinaryOp.ADD, ramp, new Field.Grid(volumetric, VOLUME_CELL_XZ, VOLUME_CELL_Y));
        return new RouterDef(
                codes.surfaceCode(),
                codes.caveCode(),
                UNUSED_CLIMATE,
                UNUSED_CLIMATE,
                codes.class3d(),
                UNUSED_CLIMATE,
                new Field.Grid(height, SURFACE_CELL_XZ, SURFACE_CELL_Y),
                new Field.Binary(Field.BinaryOp.MIN, terrain, caves),
                aquifer);
    }

    private static Field blockCenterY(DimensionDef dimension) {
        double bottom = dimension.minY();
        double top = dimension.minY() + dimension.height();
        return new Field.Coord(Axis.Y, Field.Tiling.CLAMP, bottom, top, bottom - HALF_BLOCK, top - HALF_BLOCK);
    }
}
