package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.WeightedState;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisAxisRotationClamp;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectReplace;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.structure.object.IrisObjectScale;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class VariantPlanner {
    private static final int MAX_VARIANTS = 32;
    private static final int MAX_SCALE_VARIANTS = 32;
    private static final int WARP_VARIANTS = 3;
    private static final int SPIN_STEPS = 360;
    private static final double FULL_TURN = 360;
    private static final double QUARTER = 90;
    private static final double ANGLE_PRECISION = 1.0e6;
    private static final Gson GSON = new Gson();

    private final IrisDimension dimension;
    private final IrisData data;
    private final boolean preventLeafDecay;

    VariantPlanner(IrisDimension dimension, IrisData data, boolean preventLeafDecay) {
        this.dimension = dimension;
        this.data = data;
        this.preventLeafDecay = preventLeafDecay;
    }

    Plan plan(IrisObjectPlacement placement, String resource, List<ExportDiagnostic> diagnostics) {
        List<ObjectEdit> baked = new ArrayList<>();
        List<ObjectEdit> processed = new ArrayList<>();
        for (IrisObjectReplace replace : placement.getEdit()) {
            ObjectEdit edit = edit(replace);
            if (edit == null) {
                continue;
            }
            if (edit.deterministic()) {
                baked.add(edit);
            } else {
                processed.add(edit);
            }
        }
        List<Double> scales = scales(placement);
        IrisObjectRotation rotation = placement.getRotation() == null ? new IrisObjectRotation() : placement.getRotation();
        Map<Double, Integer> xAngles = angles(rotation.getXAxis(), rotation.isEnabled());
        if (placement.getMode() == ObjectPlaceMode.CEILING_HANG) {
            xAngles = flipped(xAngles);
        }
        Map<Double, Integer> zAngles = angles(rotation.getZAxis(), rotation.isEnabled());
        List<YChoice> yChoices = yChoices(angles(rotation.getYAxis(), rotation.isEnabled()));
        List<VariantWarp> warps = warps(placement);
        List<PlannedVariant> variants = new ArrayList<>();
        boolean reshaped = false;
        for (double scale : scales) {
            for (Map.Entry<Double, Integer> x : xAngles.entrySet()) {
                for (Map.Entry<Double, Integer> z : zAngles.entrySet()) {
                    for (YChoice y : yChoices) {
                        for (VariantWarp warp : warps) {
                            VariantSpec.Rotation spin = new VariantSpec.Rotation(x.getKey(), y.residue(), z.getKey());
                            VariantSpec spec = VariantSpec.builder()
                                    .scale(scale)
                                    .rotation(spin)
                                    .warp(warp)
                                    .snow(Math.max(0, Math.min(1, placement.getSnow())))
                                    .edits(baked)
                                    .preventLeafDecay(preventLeafDecay)
                                    .build();
                            reshaped |= scale != 1 || !spin.none() || warp != null;
                            variants.add(new PlannedVariant(spec, y.quarters(), (long) x.getValue() * z.getValue() * y.weight()));
                        }
                    }
                }
            }
        }
        int planned = variants.size();
        if (planned > MAX_VARIANTS) {
            variants = subset(variants);
        }
        if (reshaped) {
            diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.INFO, ExportLimit.OBJECT_VARIANT, resource,
                    "Rotation, scale and warp export as " + variants.size() + " fixed variants"
                            + (planned > variants.size() ? " out of " + planned : "")));
        }
        return new Plan(variants, processed);
    }

    static Map<Double, Integer> angles(IrisAxisRotationClamp clamp, boolean enabled) {
        Map<Double, Integer> angles = new TreeMap<>();
        if (!enabled || clamp == null || !clamp.isEnabled()) {
            angles.put(0.0, 1);
            return angles;
        }
        if (clamp.isForceLock() || clamp.isLocked()) {
            angles.put(normalize(clamp.getMax()), 1);
            return angles;
        }
        boolean unlimited = clamp.isUnlimited();
        double interval = unlimited ? Math.max(1, clamp.getInterval()) : clamp.getInterval() <= 0 ? 1 : clamp.getInterval();
        double low = Math.min(clamp.getMin(), clamp.getMax());
        double high = Math.max(clamp.getMin(), clamp.getMax());
        for (int spin = 0; spin < SPIN_STEPS; spin++) {
            double angle = (interval * Math.ceil(spin / interval)) % FULL_TURN;
            if (!unlimited) {
                angle = Math.max(low, Math.min(high, angle));
            }
            angles.merge(normalize(angle), 1, Integer::sum);
        }
        return angles;
    }

    static List<YChoice> yChoices(Map<Double, Integer> angles) {
        Map<Double, Map<Integer, Integer>> residues = new TreeMap<>();
        for (Map.Entry<Double, Integer> angle : angles.entrySet()) {
            int quarter = (int) Math.floor(angle.getKey() / QUARTER) % 4;
            double residue = round(angle.getKey() - quarter * QUARTER);
            residues.computeIfAbsent(residue, ignored -> new TreeMap<>()).merge(quarter, angle.getValue(), Integer::sum);
        }
        List<YChoice> choices = new ArrayList<>();
        for (Map.Entry<Double, Map<Integer, Integer>> residue : residues.entrySet()) {
            Map<Integer, Integer> quarters = residue.getValue();
            int first = quarters.values().iterator().next();
            boolean even = true;
            int total = 0;
            for (int count : quarters.values()) {
                even &= count == first;
                total += count;
            }
            if (even) {
                choices.add(new YChoice(residue.getKey(), List.copyOf(quarters.keySet()), total));
                continue;
            }
            for (Map.Entry<Integer, Integer> quarter : quarters.entrySet()) {
                choices.add(new YChoice(residue.getKey(), List.of(quarter.getKey()), quarter.getValue()));
            }
        }
        return choices;
    }

    private List<Double> scales(IrisObjectPlacement placement) {
        IrisObjectScale scale = placement.getScale();
        if (scale == null) {
            return List.of(dimension == null ? 1.0 : dimension.getAllObjectScaleFactor());
        }
        if (!scale.shouldScale()) {
            return List.of(1.0);
        }
        if (scale.getSize() != 1) {
            return List.of(scale.getSize());
        }
        if (scale.getMinimumScale() == scale.getMaximumScale()) {
            return List.of(scale.getMinimumScale());
        }
        int count = Math.max(1, Math.min(scale.getVariations(), MAX_SCALE_VARIANTS));
        List<Double> scales = new ArrayList<>(count);
        double step = (scale.getMaximumScale() - scale.getMinimumScale()) / count;
        for (int index = 0; index < count; index++) {
            scales.add(scale.getMinimumScale() + step * index);
        }
        return scales;
    }

    private List<VariantWarp> warps(IrisObjectPlacement placement) {
        IrisGeneratorStyle style = placement.getWarp();
        List<VariantWarp> warps = new ArrayList<>(WARP_VARIANTS);
        if (style == null || style.isFlat()) {
            warps.add(null);
            return warps;
        }
        String key = ContentIds.hash(GSON.toJson(style));
        CNG generator = style.create(new RNG(ContentIds.seed(key)), data);
        double half = style.getMultiplier() / 2;
        WarpField field = (x, y, z) -> generator.fitDouble(-half, half, x, y, z);
        for (int index = 0; index < WARP_VARIANTS; index++) {
            warps.add(new VariantWarp(key, index, field));
        }
        return warps;
    }

    private static ObjectEdit edit(IrisObjectReplace replace) {
        if (replace == null || replace.getReplace() == null || replace.getReplace().getPalette() == null) {
            return null;
        }
        List<VanillaBlockState> find = new ArrayList<>();
        for (IrisBlockData block : replace.getFind()) {
            VanillaBlockState state = state(block);
            if (state != null) {
                find.add(state);
            }
        }
        List<WeightedState> palette = new ArrayList<>();
        for (IrisBlockData block : replace.getReplace().getPalette()) {
            VanillaBlockState state = state(block);
            if (state != null) {
                palette.add(new WeightedState(state, Math.max(1, block.getWeight())));
            }
        }
        if (find.isEmpty() || palette.isEmpty()) {
            return null;
        }
        return new ObjectEdit(find, replace.isExact(), palette, Math.max(0, Math.min(1, replace.getChance())));
    }

    private static VanillaBlockState state(IrisBlockData block) {
        if (block == null) {
            return null;
        }
        try {
            return VanillaBlockState.parse(block.stateKey());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Map<Double, Integer> flipped(Map<Double, Integer> angles) {
        Map<Double, Integer> flipped = new TreeMap<>();
        for (Map.Entry<Double, Integer> angle : angles.entrySet()) {
            flipped.merge(normalize(angle.getKey() + FULL_TURN / 2), angle.getValue(), Integer::sum);
        }
        return flipped;
    }

    private static List<PlannedVariant> subset(List<PlannedVariant> variants) {
        List<PlannedVariant> kept = new ArrayList<>(MAX_VARIANTS);
        for (int index = 0; index < MAX_VARIANTS; index++) {
            kept.add(variants.get((int) ((long) index * variants.size() / MAX_VARIANTS)));
        }
        return kept;
    }

    private static double normalize(double angle) {
        return round(((angle % FULL_TURN) + FULL_TURN) % FULL_TURN);
    }

    private static double round(double value) {
        return Math.round(value * ANGLE_PRECISION) / ANGLE_PRECISION;
    }

    record Plan(List<PlannedVariant> variants, List<ObjectEdit> processorEdits) {
    }

    record PlannedVariant(VariantSpec spec, List<Integer> quarters, long weight) {
    }

    record YChoice(double residue, List<Integer> quarters, int weight) {
    }
}
