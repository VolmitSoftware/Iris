package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BiomeDecision {
    static final int LAND = 0;
    static final int SEA = 1;
    static final int SHORE = 2;
    static final int SURFACE_SLOT = 0;
    private static final double UNDERGROUND_DEPTH = 8.0;
    private static final int BRIDGE_WEIGHT = 8;
    private static final int LEVEL_WEIGHT = 2;
    private static final int ZONE_WEIGHT = 4;
    private static final int DEPTH_WEIGHT = 2;
    private static final Field UNUSED_CODE = new Field.Const(0.0);

    private final BiomeCodes base;
    private final Parts parts;

    BiomeDecision(BiomeCodes base, Parts parts) {
        this.base = Objects.requireNonNull(base);
        this.parts = Objects.requireNonNull(parts);
    }

    public BiomeCodes base() {
        return base;
    }

    public BiomeCodes bind(Field height) {
        FieldValidator.requireTwoDimensional(Objects.requireNonNull(height), "height");
        return new BiomeCodes(surfaceCode(height), base.caveCode(), class3d(height), base.layout(), base.loadKeyByBiomeId());
    }

    private Field surfaceCode(Field height) {
        double fluid = parts.fluid();
        double fluidTop = Math.nextUp((float) fluid);
        IndexedField levels = IndexedField.partition(height, List.of(fluid - 1.0, fluid, fluidTop), List.of(0, LEVEL_WEIGHT, 2 * LEVEL_WEIGHT, 3 * LEVEL_WEIGHT));
        IndexedField above = IndexedField.partition(new Field.Binary(Field.BinaryOp.SUB, height, parts.shoreHeight()), List.of(fluidTop), List.of(0, 1));
        IndexedField key = IndexedField.sum(List.of(parts.bridge().remap(bridge -> bridge * BRIDGE_WEIGHT), levels, above));
        IndexedField list = key.remap(BiomeDecision::listFor);
        List<Field> codes = new ArrayList<>(parts.chains().size());
        for (IndexedField chain : parts.chains()) {
            codes.add(chain.lookup(index -> parts.surfaceCodes()[index]));
        }
        if (list.isConstant()) {
            return codes.get(list.values().getFirst());
        }
        List<Field> cases = new ArrayList<>(list.values().getLast() + 1);
        for (int value = 0; value <= list.values().getLast(); value++) {
            cases.add(list.values().contains(value) ? codes.get(value) : UNUSED_CODE);
        }
        return new Field.Mux(list.field(), cases);
    }

    private Field class3d(Field height) {
        if (parts.caveSlot().equals(IndexedField.constant(SURFACE_SLOT)) && parts.carvingSlots().isEmpty()) {
            return new Field.Const(0.0);
        }
        IndexedField zones = parts.zoneKeys();
        Field caveExtra = new Field.Binary(Field.BinaryOp.SUB, parts.requiredDepth(), new Field.Const(UNDERGROUND_DEPTH));
        Field caveZone = zones.lookup(key -> key / ZONE_WEIGHT == CarvingZones.CAVE_ZONE ? 1.0 : 0.0);
        Field depthGate = new Field.Binary(Field.BinaryOp.ADD, new Field.Const(UNDERGROUND_DEPTH), new Field.Gate(caveZone, caveExtra));
        Field clearance = new Field.Binary(Field.BinaryOp.SUB, new Field.Binary(Field.BinaryOp.SUB, height, parts.y()), depthGate);
        IndexedField deepEnough = IndexedField.partition(clearance, List.of(0.0), List.of(0, 1));
        IndexedField key = IndexedField.sum(List.of(zones.remap(zone -> zone / ZONE_WEIGHT * DEPTH_WEIGHT), deepEnough));
        IndexedField classIndex = key.remap(BiomeDecision::classFor);
        List<IndexedField> cases = new ArrayList<>(parts.carvingSlots().size() + 2);
        cases.add(IndexedField.constant(SURFACE_SLOT));
        cases.add(parts.caveSlot());
        cases.addAll(parts.carvingSlots());
        IndexedField slot = IndexedField.select(classIndex, cases);
        return slot.lookup(value -> value == SURFACE_SLOT ? 0.0 : parts.undergroundCodes()[value - 1]);
    }

    static int listFor(int key) {
        boolean landBase = key / BRIDGE_WEIGHT == LAND;
        int level = (key % BRIDGE_WEIGHT) / LEVEL_WEIGHT;
        boolean aboveShoreBand = key % LEVEL_WEIGHT == 1;
        boolean nearFluid = level >= 1;
        boolean belowFluid = level <= 1;
        boolean atFluid = level == 2;
        int base = landBase ? LAND : SEA;
        if (nearFluid) {
            if (!aboveShoreBand) {
                return SHORE;
            }
            if (!landBase) {
                return LAND;
            }
        } else if (!landBase && aboveShoreBand) {
            return LAND;
        }
        if (belowFluid && landBase) {
            return SEA;
        }
        if (atFluid) {
            return SHORE;
        }
        return base;
    }

    static int classFor(int key) {
        int zone = key / DEPTH_WEIGHT;
        boolean deepEnough = key % DEPTH_WEIGHT == 1;
        if (zone == CarvingZones.SHALLOW_ZONE || !deepEnough) {
            return 0;
        }
        return zone == CarvingZones.CAVE_ZONE ? 1 : zone;
    }

    record Parts(
            double fluid,
            IndexedField bridge,
            Field shoreHeight,
            List<IndexedField> chains,
            double[] surfaceCodes,
            Field y,
            IndexedField zoneKeys,
            IndexedField caveSlot,
            Field requiredDepth,
            List<IndexedField> carvingSlots,
            double[] undergroundCodes) {
        Parts {
            Objects.requireNonNull(bridge);
            Objects.requireNonNull(shoreHeight);
            chains = List.copyOf(chains);
            Objects.requireNonNull(surfaceCodes);
            Objects.requireNonNull(y);
            Objects.requireNonNull(zoneKeys);
            Objects.requireNonNull(caveSlot);
            Objects.requireNonNull(requiredDepth);
            carvingSlots = List.copyOf(carvingSlots);
            Objects.requireNonNull(undergroundCodes);
        }
    }
}
