package art.arcane.iris.world.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class DenseTransitionGeometryReference {
    private static final int MAXIMUM_OPENING_HEIGHT = 64;
    private static final BoundaryColumnGeometry.Voxel AIR = new BoundaryColumnGeometry.Voxel(
            "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
    private static final BoundaryColumnGeometry.Voxel FLUID_BARRIER = new BoundaryColumnGeometry.Voxel(
            "minecraft:obsidian", BoundaryColumnGeometry.Phase.SOLID, "", false);

    private DenseTransitionGeometryReference() {
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
            changed |= !value.equals(current.voxels.get(offset));
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
        BoundaryColumnGeometry.Voxel currentVoxel = current.voxels.get(offset);
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
            if (profile.voxels.get(offset).protectedContent()) {
                continue;
            }
            historicalOpening |= profile.enclosedOpenings[offset];
            double weight = contribution.weight();
            total += weight;
            solid += profile.solidDistances[offset] * weight;
            fluid += profile.fluidDistances[offset] * weight;
            if (profile.solidMaterials[offset] != null && weight > solidMaterialWeight) {
                oldSolid = profile.solidMaterials[offset];
                solidMaterialWeight = weight;
            }
            if (profile.fluidMaterials[offset] != null && weight > fluidMaterialWeight) {
                oldFluid = profile.fluidMaterials[offset];
                fluidMaterialWeight = weight;
            }
        }
        if (total == 0D) {
            return currentVoxel;
        }
        double blendedSolid = GenerationBlend.interpolate(solid / total,
                current.solidDistances[offset], historicalOpening ? openingWeight : currentWeight);
        if (blendedSolid > 0D) {
            return selectMaterial(oldSolid, current.solidMaterials[offset], historicalMaterial);
        }
        double blendedFluid = GenerationBlend.interpolate(fluid / total,
                current.fluidDistances[offset], historicalOpening ? openingWeight : currentWeight);
        if (blendedFluid > 0D) {
            BoundaryColumnGeometry.Voxel newFluid = current.fluidMaterials[offset];
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

    private static BoundaryColumnGeometry.Voxel[] nearestMaterials(
            List<BoundaryColumnGeometry.Voxel> voxels,
            BoundaryColumnGeometry.Phase phase
    ) {
        BoundaryColumnGeometry.Voxel[] materials = new BoundaryColumnGeometry.Voxel[voxels.size()];
        int[] distances = new int[voxels.size()];
        int last = -voxels.size() - 1;
        BoundaryColumnGeometry.Voxel nearest = null;
        for (int index = 0; index < voxels.size(); index++) {
            BoundaryColumnGeometry.Voxel voxel = voxels.get(index);
            if (voxel.phase() == phase && !voxel.protectedContent()) {
                nearest = voxel;
                last = index;
            }
            materials[index] = nearest;
            distances[index] = index - last;
        }
        last = voxels.size() * 2;
        nearest = null;
        for (int index = voxels.size() - 1; index >= 0; index--) {
            BoundaryColumnGeometry.Voxel voxel = voxels.get(index);
            if (voxel.phase() == phase && !voxel.protectedContent()) {
                nearest = voxel;
                last = index;
            }
            if (nearest != null && last - index < distances[index]) {
                materials[index] = nearest;
            }
        }
        return materials;
    }

    private static boolean[] enclosedOpenings(List<BoundaryColumnGeometry.Voxel> voxels) {
        boolean[] openings = new boolean[voxels.size()];
        int lowerSolid = -1;
        for (int index = 0; index < voxels.size(); index++) {
            BoundaryColumnGeometry.Voxel voxel = voxels.get(index);
            if (voxel.phase() == BoundaryColumnGeometry.Phase.SOLID && !voxel.protectedContent()) {
                if (lowerSolid >= 0 && index - lowerSolid - 1 <= MAXIMUM_OPENING_HEIGHT) {
                    for (int opening = lowerSolid + 1; opening < index; opening++) {
                        openings[opening] = !voxels.get(opening).protectedContent();
                    }
                }
                lowerSolid = index;
            }
        }
        return openings;
    }

    private static final class ColumnProfile {
        private final List<BoundaryColumnGeometry.Voxel> voxels;
        private final boolean[] enclosedOpenings;
        private final double[] solidDistances;
        private final double[] fluidDistances;
        private final BoundaryColumnGeometry.Voxel[] solidMaterials;
        private final BoundaryColumnGeometry.Voxel[] fluidMaterials;

        private ColumnProfile(BoundaryColumnGeometry geometry) {
            voxels = geometry.voxels();
            enclosedOpenings = enclosedOpenings(voxels);
            solidDistances = geometry.solidDistances();
            fluidDistances = geometry.fluidDistances();
            solidMaterials = nearestMaterials(voxels, BoundaryColumnGeometry.Phase.SOLID);
            fluidMaterials = nearestMaterials(voxels, BoundaryColumnGeometry.Phase.FLUID);
        }
    }

    private record WeightedProfile(ColumnProfile profile, double weight) {
    }
}
