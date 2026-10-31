package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.FieldDomains;

import java.util.Objects;
import java.util.Optional;

public final class DeepCaveCompiler {
    static final int LAVA_LINE = -54;
    static final String FIELD = "cave/field";
    static final String OPENING = "cave/deep_opening";
    private static final double OPEN_MARGIN = Math.nextUp(Float.MIN_VALUE);
    private static final Field OPEN = new Field.Const(1.0);
    private static final Field INSIDE = new Field.Const(0.0);
    private static final Field CLOSED = new Field.Const(CaveProfileCompiler.SOLID);
    private static final Field UNMASKED = new Field.Const(-FieldDomains.CONSTANT_LIMIT);

    private final CaveInputs inputs;
    private final Field blockY;

    public DeepCaveCompiler(CaveInputs inputs) {
        this.inputs = Objects.requireNonNull(inputs);
        this.blockY = inputs.blockY();
    }

    public Optional<DeepCaves> compile(Field.Shared field, CaveSelection selection) {
        Optional<Zone> found = zone(selection);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Zone zone = found.get();
        inputs.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.DEEP_CAVE_LAYERS, inputs.resource(),
                "Caves between Y " + zone.low() + " and Y " + zone.high() + " open in the material pass, so they get wall palettes but no floor or ceiling layers"));
        Field opened = new Field.Binary(Field.BinaryOp.MAX, field, inside(zone, INSIDE, CLOSED));
        Field.Shared opening = new Field.Shared(OPENING,
                new Field.Gate(new Field.Binary(Field.BinaryOp.SUB, new Field.Const(OPEN_MARGIN), opened), OPEN));
        Field mask = new Field.Binary(Field.BinaryOp.ADD, opening, inside(zone, INSIDE, UNMASKED));
        return Optional.of(new DeepCaves(new Field.Binary(Field.BinaryOp.MAX, field, mask), opening));
    }

    private Optional<Zone> zone(CaveSelection selection) {
        int high = Math.min(line() - 1, inputs.maxY());
        int low = inputs.minY();
        int deepest = Integer.MAX_VALUE;
        for (CaveSource source : selection.sources()) {
            deepest = Math.min(deepest, CaveProfileCompiler.floor(inputs, source));
            if (source.profile().isAllowLava()) {
                low = Math.max(low, inputs.lavaY() + 1);
            }
        }
        if (high < low || high < deepest) {
            return Optional.empty();
        }
        return Optional.of(new Zone(low, high));
    }

    private int line() {
        return Math.min(LAVA_LINE, DimensionDef.seaLevel(inputs.fluidHeight()));
    }

    private Field inside(Zone zone, Field inRange, Field outOfRange) {
        return new Field.RangeIf(blockY, zone.low(), zone.high() + 1.0, inRange, outOfRange);
    }

    public record DeepCaves(Field router, Field.Shared opening) {
        public DeepCaves {
            Objects.requireNonNull(router);
            Objects.requireNonNull(opening);
        }
    }

    private record Zone(int low, int high) {
    }
}
