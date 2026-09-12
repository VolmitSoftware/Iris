package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.StructurePlacementGrid;
import art.arcane.iris.structure.placement.StructurePlacementScope;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.structure.placement.IrisStructureAnchorMode;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class NativeStructurePlacementPlanner {
    private static final long NATIVE_SELECTION_DOMAIN = 0x4E41544956454A53L;

    private NativeStructurePlacementPlanner() {
    }

    public static KList<NativeStructureStartPlan> plansAt(Engine engine, int chunkX, int chunkZ) {
        Engine activeEngine = Objects.requireNonNull(engine, "Native structure planner requires an engine");
        if (!allowsStart(activeEngine, chunkX, chunkZ)) {
            return new KList<>();
        }
        Map<String, NativeStructureStartPlan> plansByStructure = new LinkedHashMap<>();
        for (IrisStructurePlacement placement : StructurePlacementScope.placementsAt(activeEngine, chunkX, chunkZ)) {
            NativeStructureStartPlan plan = planAt(activeEngine, placement, chunkX, chunkZ);
            if (plan != null) {
                String structureKey = normalize(plan.source().getStructure());
                NativeStructureStartPlan current = plansByStructure.get(structureKey);
                if (current == null || comparePlacementPriority(plan.placement(), current.placement()) < 0) {
                    plansByStructure.put(structureKey, plan);
                }
            }
        }
        return new KList<>(plansByStructure.values());
    }

    public static NativeStructureStartPlan planAt(Engine engine, IrisStructurePlacement placement,
                                                   int chunkX, int chunkZ) {
        validateBackend(placement);
        if (!placement.hasNativeStructures()) {
            return null;
        }
        Engine activeEngine = Objects.requireNonNull(engine, "Native structure planner requires an engine");
        if (!allowsStart(activeEngine, chunkX, chunkZ)) {
            return null;
        }
        if (activeEngine.getSeedManager() == null) {
            throw new IllegalStateException("Native structure planner requires a bound seed manager");
        }
        long mantleSeed = activeEngine.getSeedManager().getMantle();
        if (!StructurePlacementGrid.startsInChunk(placement, chunkX, chunkZ, mantleSeed)) {
            return null;
        }
        RNG placementRng = StructurePlacementGrid.placementRng(
                placement, chunkX, chunkZ, mantleSeed).nextParallelRNG(NATIVE_SELECTION_DOMAIN);
        IrisNativeStructure source = selectSource(placement.getNativeStructures(), placementRng);
        Integer baseY = resolveBaseY(activeEngine, placement, chunkX, chunkZ, placementRng);
        if (baseY == null) {
            return null;
        }
        return new NativeStructureStartPlan(placement, source, chunkX, chunkZ, baseY);
    }

    public static NativeStructureStartPlan matchingPlan(Engine engine, String structureKey,
                                                         int chunkX, int chunkZ) {
        if (structureKey == null || structureKey.isBlank()) {
            return null;
        }
        String normalizedKey = normalize(structureKey);
        for (NativeStructureStartPlan plan : plansAt(engine, chunkX, chunkZ)) {
            if (normalizedKey.equals(normalize(plan.source().getStructure()))) {
                return plan;
            }
        }
        return null;
    }

    public static IrisNativeStructureDecision decisionFor(NativeStructureStartPlan plan) {
        Objects.requireNonNull(plan, "Native structure start plan must not be null");
        IrisStructurePlacement placement = plan.placement();
        return new IrisNativeStructureDecision(
                NativeStructureGenerationStatus.GENERATE_NATIVE,
                0,
                null,
                false,
                placement.getStilt(),
                placement.resolvedTerrain()
        );
    }

    public static void validateBackend(IrisStructurePlacement placement) {
        if (placement == null) {
            throw new IllegalStateException("Structure placement must not be null");
        }
        boolean iris = placement.hasIrisStructures();
        boolean nativeStructure = placement.hasNativeStructures();
        if (iris == nativeStructure) {
            throw new IllegalStateException("Structure placement must declare exactly one backend: "
                    + "structures or nativeStructures");
        }
        if (nativeStructure && placement.getAnchor() != null
                && placement.getAnchor() != IrisStructureAnchorMode.LEGACY) {
            throw new IllegalStateException("Native structure placements do not support the Iris anchor field");
        }
    }

    static IrisNativeStructure selectSource(KList<IrisNativeStructure> sources, RNG rng) {
        Objects.requireNonNull(sources, "Native structure sources must not be null");
        Objects.requireNonNull(rng, "Native structure selection requires an RNG");
        long totalWeight = 0L;
        for (IrisNativeStructure source : sources) {
            if (source == null || source.getStructure() == null || source.getStructure().isBlank()) {
                throw new IllegalStateException("Native structure placement contains a blank source");
            }
            if (source.getWeight() < 1) {
                throw new IllegalStateException("Native structure source '" + source.getStructure()
                        + "' has invalid weight " + source.getWeight());
            }
            totalWeight = Math.addExact(totalWeight, source.getWeight());
        }
        if (totalWeight > Integer.MAX_VALUE) {
            throw new IllegalStateException("Native structure source weights exceed " + Integer.MAX_VALUE);
        }
        int roll = rng.nextInt((int) totalWeight);
        for (IrisNativeStructure source : sources) {
            roll -= source.getWeight();
            if (roll < 0) {
                return source;
            }
        }
        throw new IllegalStateException("Native structure source selection produced no result");
    }

    private static Integer resolveBaseY(Engine engine, IrisStructurePlacement placement,
                                        int chunkX, int chunkZ, RNG rng) {
        int worldMin = engine.getMinHeight() + 1;
        int worldMax = engine.getMinHeight() + engine.getHeight() - 1;
        if (worldMin > worldMax) {
            throw new IllegalStateException("Native structure placement has invalid world height bounds");
        }
        int blockX = (chunkX << 4) + 8;
        int blockZ = (chunkZ << 4) + 8;
        if (!placement.isUnderwater()
                && isSubmerged(engine, blockX, blockZ)) {
            return null;
        }
        if (!placement.isUnderground()) {
            int surfaceY = Engine.hostHeight(engine, blockX, blockZ, true) + engine.getMinHeight();
            if (surfaceY < placement.getMinHeight() || surfaceY > placement.getMaxHeight()) {
                return null;
            }
            return surfaceY;
        }
        int bandMin = Math.max(worldMin, Math.min(placement.getMinHeight(), placement.getMaxHeight()));
        int bandMax = Math.min(worldMax, Math.max(placement.getMinHeight(), placement.getMaxHeight()));
        if (bandMin > bandMax) {
            return null;
        }
        return bandMin == bandMax ? bandMin : bandMin + rng.nextInt((bandMax - bandMin) + 1);
    }

    public static boolean isSubmerged(Engine engine, int blockX, int blockZ) {
        int localFluidHeight = engine.getComplex() == null
                ? engine.getDimension().getFluidHeight()
                : (int) Math.round(engine.getComplex().getRiverWaterSurfaceStream().get(blockX, blockZ));
        return Engine.hostHeight(engine, blockX, blockZ, true) < localFluidHeight;
    }

    private static boolean allowsStart(Engine engine, int chunkX, int chunkZ) {
        IrisComplex complex = engine.getComplex();
        return complex == null || complex.allowsNewGenerationChunk(chunkX, chunkZ);
    }

    private static int comparePlacementPriority(IrisStructurePlacement left, IrisStructurePlacement right) {
        long leftIdentity = StructurePlacementGrid.placementIdentity(left);
        long rightIdentity = StructurePlacementGrid.placementIdentity(right);
        return Long.compareUnsigned(leftIdentity, rightIdentity);
    }

    private static String normalize(String structureKey) {
        return structureKey == null ? "" : structureKey.toLowerCase(Locale.ROOT);
    }

}
