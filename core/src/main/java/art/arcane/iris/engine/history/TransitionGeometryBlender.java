package art.arcane.iris.engine.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TransitionGeometryBlender {
    private static final int MAXIMUM_OPENING_HEIGHT = 64;
    private static final BoundaryColumnGeometry.Voxel AIR = new BoundaryColumnGeometry.Voxel(
            "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
    private static final BoundaryColumnGeometry.Voxel FLUID_BARRIER = new BoundaryColumnGeometry.Voxel(
            "minecraft:obsidian", BoundaryColumnGeometry.Phase.SOLID, "", false);

    private TransitionGeometryBlender() {
    }

    public static BoundaryColumnGeometry blendColumn(
            TransitionGenerationPlan plan,
            int blockX,
            int blockZ,
            BoundaryColumnGeometry currentGeometry
    ) {
        Objects.requireNonNull(plan, "Transition plan");
        return blendColumn(plan.geometryAt(blockX, blockZ), blockX, blockZ, currentGeometry);
    }

    public static BoundaryColumnGeometry blendColumn(
            BoundaryGeometryInfluence influence,
            int blockX,
            int blockZ,
            BoundaryColumnGeometry currentGeometry
    ) {
        Objects.requireNonNull(influence, "Boundary influence");
        Objects.requireNonNull(currentGeometry, "Current geometry");
        if (influence.newTerrainWeight() == 1D || influence.contributions().isEmpty()
                || currentGeometry.height() == 0) {
            return currentGeometry;
        }
        ColumnProfile current = new ColumnProfile(currentGeometry);
        ArrayList<WeightedProfile> historical = new ArrayList<>(influence.contributions().size());
        for (BoundaryGeometryInfluence.Contribution contribution : influence.contributions()) {
            BoundaryColumnGeometry geometry = contribution.geometry();
            if (geometry.minimumY() != currentGeometry.minimumY() || geometry.height() != currentGeometry.height()) {
                throw new IllegalArgumentException("Boundary geometry does not match the current vertical layout");
            }
            historical.add(new WeightedProfile(new ColumnProfile(geometry), contribution.weight()));
        }
        ArrayList<BoundaryColumnGeometry.Voxel> result = new ArrayList<>(currentGeometry.height());
        boolean changed = false;
        for (int offset = 0; offset < currentGeometry.height(); offset++) {
            BoundaryColumnGeometry.Voxel value = blendVoxel(current, historical, offset,
                    influence.newTerrainWeight(), influence.openingWeight(), GenerationBlend.usesHistoricalMaterial(blockX,
                            currentGeometry.minimumY() + offset, blockZ, influence.newTerrainWeight()));
            result.add(value);
            changed |= !value.equals(current.voxelAt(offset));
        }
        return changed ? BoundaryColumnGeometry.fromVoxels(currentGeometry.minimumY(), result) : currentGeometry;
    }

    private static BoundaryColumnGeometry.Voxel blendVoxel(
            ColumnProfile current,
            List<WeightedProfile> historical,
            int offset,
            double currentWeight,
            double openingWeight,
            boolean historicalMaterial
    ) {
        BoundaryColumnGeometry.Voxel currentVoxel = current.voxelAt(offset);
        if (currentVoxel.protectedContent()) {
            return currentVoxel;
        }
        boolean historicalOpening = false;
        double solid = 0D;
        double fluid = 0D;
        double total = 0D;
        double solidMaterialWeight = -1D;
        double fluidMaterialWeight = -1D;
        BoundaryColumnGeometry.Voxel oldSolid = null;
        BoundaryColumnGeometry.Voxel oldFluid = null;
        for (WeightedProfile contribution : historical) {
            ColumnProfile profile = contribution.profile();
            if (profile.voxelAt(offset).protectedContent()) {
                continue;
            }
            historicalOpening |= profile.enclosedOpeningAt(offset);
            double weight = contribution.weight();
            total += weight;
            solid += profile.solid.distanceAt(offset) * weight;
            fluid += profile.fluid.distanceAt(offset) * weight;
            if (profile.solid.materialAt(offset) != null && weight > solidMaterialWeight) {
                oldSolid = profile.solid.materialAt(offset);
                solidMaterialWeight = weight;
            }
            if (profile.fluid.materialAt(offset) != null && weight > fluidMaterialWeight) {
                oldFluid = profile.fluid.materialAt(offset);
                fluidMaterialWeight = weight;
            }
        }
        if (total == 0D) {
            return currentVoxel;
        }
        double blendedSolid = GenerationBlend.interpolate(solid / total,
                current.solid.distanceAt(offset), historicalOpening ? openingWeight : currentWeight);
        if (blendedSolid > 0D) {
            return selectMaterial(oldSolid, current.solid.materialAt(offset), historicalMaterial);
        }
        double blendedFluid = GenerationBlend.interpolate(fluid / total,
                current.fluid.distanceAt(offset), historicalOpening ? openingWeight : currentWeight);
        if (blendedFluid > 0D) {
            BoundaryColumnGeometry.Voxel newFluid = current.fluid.materialAt(offset);
            if (oldFluid != null && newFluid != null && !fluidFamily(oldFluid).equals(fluidFamily(newFluid))) {
                return FLUID_BARRIER;
            }
            return selectMaterial(oldFluid, newFluid, currentWeight <= 0.5D);
        }
        return AIR;
    }

    private static String fluidFamily(BoundaryColumnGeometry.Voxel voxel) {
        String key = voxel.fluidStateKey();
        int properties = key.indexOf('[');
        return properties < 0 ? key : key.substring(0, properties);
    }

    private static BoundaryColumnGeometry.Voxel selectMaterial(
            BoundaryColumnGeometry.Voxel historical,
            BoundaryColumnGeometry.Voxel current,
            boolean historicalMaterial
    ) {
        if (historical == null) {
            return current == null ? AIR : current;
        }
        return current == null || historicalMaterial ? historical : current;
    }

    private static final class ColumnProfile {
        private final BoundaryColumnGeometry geometry;
        private final PhaseCursor solid;
        private final PhaseCursor fluid;
        private int run;

        private ColumnProfile(BoundaryColumnGeometry geometry) {
            this.geometry = geometry;
            solid = new PhaseCursor(geometry, BoundaryColumnGeometry.Phase.SOLID);
            fluid = new PhaseCursor(geometry, BoundaryColumnGeometry.Phase.FLUID);
        }

        private BoundaryColumnGeometry.Voxel voxelAt(int offset) {
            while (offset >= geometry.runEnd(run)) {
                run++;
            }
            return geometry.runVoxel(run);
        }

        private boolean enclosedOpeningAt(int offset) {
            solid.advanceTo(offset);
            return !solid.occupied && solid.start > 0 && solid.end < geometry.height()
                    && solid.end - solid.start <= MAXIMUM_OPENING_HEIGHT
                    && !voxelAt(offset).protectedContent();
        }
    }

    private static final class PhaseCursor {
        private final BoundaryColumnGeometry geometry;
        private final BoundaryColumnGeometry.Phase phase;
        private int start;
        private int end;
        private int nextRun;
        private int materialRun;
        private boolean occupied;
        private BoundaryColumnGeometry.Voxel lowerMaterial;
        private BoundaryColumnGeometry.Voxel upperMaterial;

        private PhaseCursor(BoundaryColumnGeometry geometry, BoundaryColumnGeometry.Phase phase) {
            this.geometry = geometry;
            this.phase = phase;
            advanceTo(0);
        }

        private void advanceTo(int offset) {
            while (offset >= end) {
                start = end;
                occupied = matches(geometry.runVoxel(nextRun));
                lowerMaterial = start == 0 ? null : geometry.runVoxel(nextRun - 1);
                do {
                    end = geometry.runEnd(nextRun++);
                } while (nextRun < geometry.runCount() && matches(geometry.runVoxel(nextRun)) == occupied);
                upperMaterial = nextRun < geometry.runCount() ? geometry.runVoxel(nextRun) : null;
            }
        }

        private double distanceAt(int offset) {
            advanceTo(offset);
            double lowerDistance = start == 0 ? geometry.height() + offset + 1.5D : offset - start + 0.5D;
            double upperDistance = end == geometry.height()
                    ? geometry.height() * 2D - offset + 0.5D : end - offset - 0.5D;
            return Math.min(lowerDistance, upperDistance) * (occupied ? 1D : -1D);
        }

        private BoundaryColumnGeometry.Voxel materialAt(int offset) {
            advanceTo(offset);
            if (occupied) {
                while (offset >= geometry.runEnd(materialRun)) {
                    materialRun++;
                }
                return geometry.runVoxel(materialRun);
            }
            if (lowerMaterial == null) {
                return upperMaterial;
            }
            return upperMaterial != null && end - offset < offset - start + 1
                    ? upperMaterial : lowerMaterial;
        }

        private boolean matches(BoundaryColumnGeometry.Voxel voxel) {
            return voxel.phase() == phase && !voxel.protectedContent();
        }
    }

    private record WeightedProfile(ColumnProfile profile, double weight) {
    }
}
