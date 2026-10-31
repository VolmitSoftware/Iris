package art.arcane.iris.pack.datapack.export.compile.cave;

import art.arcane.iris.generation.cave.IrisCaveProfile;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class AquiferCompiler {
    private static final int LAVA_LIMIT_Y = -10;
    private static final int LAVA_CENTER_MARGIN = 12;
    private static final int FLUID_BAND_HEIGHT = 40;
    private static final int FLUID_BAND_CENTER = 20;
    private static final double SPREAD_LEVEL_SCALE = 10.0;
    private static final double LEVEL_ROUNDING_MARGIN = 0.5;
    private static final int OPEN_WATER_DEPTH = 8;
    private static final int OPEN_WATER_HEADROOM = 8;
    private static final int OPEN_WATER_REACH = 32;
    private static final int OPEN_WATER_SHORE = 8;
    private static final int OPEN_WATER_ESTIMATE_SLACK = 40;
    private static final Field OPEN_WATER_LIFT = new Field.Const(2.0);
    private static final Field DRY = new Field.Const(-1.0);
    private static final Field PARTIAL = new Field.Const(0.6);
    private static final Field LOW_BARRIER = new Field.Const(-1.0);
    private static final Field NO_EXCLUSION = new Field.Const(-1.0);
    private static final Field LAVA = new Field.Const(1.0);
    private static final Field NO_LAVA = new Field.Const(0.0);
    private static final Field OUTSIDE_BAND = new Field.Const(0.0);
    private static final Field INSIDE_BAND = new Field.Const(1.0);

    private final CaveInputs inputs;
    private final Field blockY;
    private final int lowFluidBand;
    private final int highFluidBand;

    public AquiferCompiler(CaveInputs inputs) {
        this.inputs = Objects.requireNonNull(inputs);
        this.blockY = inputs.blockY();
        this.lowFluidBand = Math.floorDiv(inputs.minY(), FLUID_BAND_HEIGHT) - 1;
        this.highFluidBand = Math.floorDiv(inputs.maxY(), FLUID_BAND_HEIGHT) + 1;
    }

    public AquiferDef compile(CaveSelection selection) {
        AquiferDef dry = AquiferDef.dry(inputs.aquiferSurface());
        boolean lavaWanted = any(selection, IrisCaveProfile::isAllowLava);
        boolean lavaExported = lavaWanted && inputs.lavaY() <= LAVA_LIMIT_Y;
        if (lavaWanted && !lavaExported) {
            inputs.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.LAVA_HEIGHT, inputs.resource(),
                    "Cave lava height Y " + inputs.lavaY() + " is above Y " + LAVA_LIMIT_Y + "; caves export without lava"));
        }
        boolean wet = any(selection, IrisCaveProfile::isAllowFluid);
        if (!lavaExported && !wet) {
            return new AquiferDef(dry.barrier(), openWater(dry.floodedness()), dry.spread(), dry.lava(), dry.exclusion(), dry.surfaceLevel());
        }
        Field spread = lavaExported ? lavaSpread(wet, dry.spread()) : dry.spread();
        Field lava = lavaExported ? lavaBands() : NO_LAVA;
        return new AquiferDef(LOW_BARRIER, openWater(floodedness(selection, lavaExported)), spread, lava, NO_EXCLUSION, inputs.aquiferSurface());
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

    private Field floodedness(CaveSelection selection, boolean lavaExported) {
        Field flood = selection.profiles().isEmpty() ? DRY : selection.select(source -> flood(source, lavaExported), DRY);
        for (CaveSource band : selection.bands()) {
            Field banded = flood(band, lavaExported);
            if (banded.equals(flood)) {
                continue;
            }
            Field inside = new Field.Partition(blockY, List.of((double) band.minY(), band.maxY() + 1.0), List.of(OUTSIDE_BAND, INSIDE_BAND, OUTSIDE_BAND));
            flood = new Field.Mux(inside, List.of(flood, banded));
        }
        return flood;
    }

    private Field flood(CaveSource source, boolean lavaExported) {
        IrisCaveProfile profile = source.profile();
        int top = Integer.MIN_VALUE;
        if (lavaExported && profile.isAllowLava()) {
            top = inputs.lavaY() + LAVA_CENTER_MARGIN;
        }
        if (profile.isAllowFluid()) {
            top = Math.max(top, inputs.fluidHeight());
        }
        if (top < inputs.minY()) {
            return DRY;
        }
        if (top >= inputs.maxY()) {
            return PARTIAL;
        }
        return new Field.RangeIf(blockY, inputs.minY(), top + 1.0, PARTIAL, DRY);
    }

    private Field lavaSpread(boolean wet, Field vanillaSpread) {
        Field levels = new Field.Coord(Axis.Y, Field.Tiling.CLAMP, lowFluidBand, highFluidBand, spreadAt(lowFluidBand), spreadAt(highFluidBand));
        return wet ? new Field.RangeIf(fluidBand(), lowFluidBand, lavaBand() + 1.0, levels, vanillaSpread) : levels;
    }

    private Field lavaBands() {
        return new Field.RangeIf(fluidBand(), lowFluidBand, lavaBand() + 1.0, LAVA, NO_LAVA);
    }

    private Field fluidBand() {
        return new Field.Coord(Axis.Y, Field.Tiling.CLAMP, lowFluidBand, highFluidBand, lowFluidBand, highFluidBand);
    }

    private int lavaBand() {
        return Math.floorDiv(inputs.lavaY() + LAVA_CENTER_MARGIN, FLUID_BAND_HEIGHT);
    }

    private double spreadAt(int band) {
        return (inputs.lavaY() - FLUID_BAND_CENTER + LEVEL_ROUNDING_MARGIN) / SPREAD_LEVEL_SCALE - band * (FLUID_BAND_HEIGHT / SPREAD_LEVEL_SCALE);
    }

    private static boolean any(CaveSelection selection, Predicate<IrisCaveProfile> property) {
        for (CaveSource source : selection.sources()) {
            if (property.test(source.profile())) {
                return true;
            }
        }
        return false;
    }
}
