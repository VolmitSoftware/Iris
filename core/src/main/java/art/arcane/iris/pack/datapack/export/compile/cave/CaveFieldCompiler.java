package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class CaveFieldCompiler {
    static final int CELL_XZ = 4;
    static final int CELL_Y = 4;
    static final double WALL_BAND = 0.03125;
    private static final double MIN_GAIN = 1.0 / 64.0;
    private static final double MAX_GAIN = 64.0;
    private static final Field UNCOVERED = new Field.Const(CaveProfileCompiler.SOLID);

    private final CaveProfileCompiler profiles;
    private final CaveInputs inputs;

    public CaveFieldCompiler(StyleCompiler styles, CaveInputs inputs) {
        this.profiles = new CaveProfileCompiler(styles, inputs);
        this.inputs = inputs;
    }

    public Optional<Field> compile(CaveSelection selection) {
        return union(selection).map(union -> new Field.Grid(union, CELL_XZ, CELL_Y));
    }

    private Optional<Field> union(CaveSelection selection) {
        List<Field> parts = new ArrayList<>(selection.bands().size() + 1);
        if (!selection.profiles().isEmpty()) {
            parts.add(selection.select(this::normalized, UNCOVERED));
        }
        for (CaveSource band : selection.bands()) {
            parts.add(normalized(band));
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        Field union = parts.getFirst();
        for (int index = 1; index < parts.size(); index++) {
            union = new Field.Binary(Field.BinaryOp.MIN, union, parts.get(index));
        }
        return Optional.of(union);
    }

    private Field normalized(CaveSource source) {
        return profiles.compile(source, gain(source));
    }

    private double gain(CaveSource source) {
        Optional<CaveInterior> interior = profiles.interior(source);
        if (interior.isEmpty()) {
            return 1.0;
        }
        double band = WallShellCalibrator.band(interior.get(), inputs.noises().byReference(), inputs.capabilities().target());
        return band > 0.0 ? Math.clamp(WALL_BAND / band, MIN_GAIN, MAX_GAIN) : 1.0;
    }
}
