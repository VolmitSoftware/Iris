package art.arcane.iris.world.history;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class DenseTransitionGeometryReference {
    private static final BoundaryColumnGeometry.Voxel AIR = new BoundaryColumnGeometry.Voxel(
            "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
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
                    influence.newTerrainWeight());
            result.add(value);
            changed |= !value.equals(current.voxels.get(offset));
        }
        return changed ? BoundaryColumnGeometry.fromVoxels(currentGeometry.minimumY(), result) : currentGeometry;
    }

    private static BoundaryColumnGeometry.Voxel blendVoxel(
            ColumnProfile current, List<WeightedProfile> historical, int offset, double currentWeight
    ) {
        BoundaryColumnGeometry.Voxel currentVoxel = current.voxels.get(offset);
        if (currentVoxel.protectedContent() || offset == 0) {
            return currentVoxel;
        }
        double solid = 0D;
        double total = 0D;
        double fluidWeight = 0D;
        BoundaryColumnGeometry.Voxel oldSolid = null;
        BoundaryColumnGeometry.Voxel oldFluid = null;
        for (WeightedProfile contribution : historical) {
            ColumnProfile profile = contribution.profile();
            BoundaryColumnGeometry.Voxel voxel = profile.voxels.get(offset);
            if (voxel.protectedContent()) {
                continue;
            }
            total += contribution.weight();
            solid += profile.solidDistances[offset] * contribution.weight();
            if (oldSolid == null) {
                oldSolid = profile.solidMaterials[offset];
            }
            if (voxel.phase() == BoundaryColumnGeometry.Phase.FLUID
                    && profile.solidAbove[offset] && contribution.weight() > fluidWeight) {
                oldFluid = voxel;
                fluidWeight = contribution.weight();
            }
        }
        if (total == 0D) {
            return currentVoxel;
        }
        if (GenerationBlend.interpolate(solid / total, current.solidDistances[offset], currentWeight) > 0D) {
            BoundaryColumnGeometry.Voxel material = current.solidMaterials[offset];
            return material != null ? material : oldSolid != null ? oldSolid : currentVoxel;
        }
        if (currentVoxel.phase() == BoundaryColumnGeometry.Phase.FLUID) {
            return currentVoxel;
        }
        return oldFluid != null && currentWeight < 0.5D ? oldFluid : AIR;
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

    private static final class ColumnProfile {
        private final List<BoundaryColumnGeometry.Voxel> voxels;
        private final boolean[] solidAbove;
        private final double[] solidDistances;
        private final BoundaryColumnGeometry.Voxel[] solidMaterials;

        private ColumnProfile(BoundaryColumnGeometry geometry) {
            voxels = geometry.voxels();
            solidDistances = geometry.solidDistances();
            solidMaterials = nearestMaterials(voxels, BoundaryColumnGeometry.Phase.SOLID);
            solidAbove = new boolean[voxels.size()];
            boolean solidSeen = false;
            for (int index = voxels.size() - 1; index >= 0; index--) {
                solidAbove[index] = solidSeen;
                solidSeen |= voxels.get(index).phase() == BoundaryColumnGeometry.Phase.SOLID;
            }
        }
    }

    private record WeightedProfile(ColumnProfile profile, double weight) {
    }
}
