package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

final class FootprintMutableColumn {
    private final int x;
    private final int z;
    private final int naturalHeight;
    private final int seaLevel;
    private final boolean ocean;
    private final String parentBiomeKey;
    private HydrologyColumnLayer singleLayer;
    private LinkedHashMap<Long, HydrologyColumnLayer> layers;

    FootprintMutableColumn(int x, int z, HydrologyTerrainSample terrain, int seaLevel) {
        this.x = x;
        this.z = z;
        this.naturalHeight = terrain.naturalHeight();
        this.seaLevel = seaLevel;
        this.ocean = terrain.ocean();
        this.parentBiomeKey = terrain.parentBiomeKey();
    }

    FootprintMutableColumn(HydrologyColumnSample sample) {
        this.x = sample.x();
        this.z = sample.z();
        this.naturalHeight = sample.naturalHeight();
        this.seaLevel = sample.seaLevel();
        this.ocean = sample.ocean();
        this.parentBiomeKey = sample.parentBiomeKey();
        for (HydrologyColumnLayer layer : sample.layers()) {
            add(layer);
        }
    }

    void add(HydrologyColumnLayer layer) {
        if (layers == null) {
            if (singleLayer == null) {
                singleLayer = layer;
                return;
            }
            if (singleLayer.feature().id() == layer.feature().id()) {
                singleLayer = merge(singleLayer, layer);
                return;
            }
            layers = new LinkedHashMap<>();
            layers.put(singleLayer.feature().id(), singleLayer);
            singleLayer = null;
        }
        HydrologyColumnLayer existing = layers.get(layer.feature().id());
        layers.put(layer.feature().id(), existing == null ? layer : merge(existing, layer));
    }

    HydrologyColumnSample build() {
        List<HydrologyColumnLayer> builtLayers;
        if (layers != null) {
            builtLayers = resolveSurfaceHeadConflicts(new ArrayList<>(layers.values()));
        } else if (singleLayer != null) {
            builtLayers = List.of(singleLayer);
        } else {
            builtLayers = List.of();
        }
        return new HydrologyColumnSample(
                x,
                z,
                naturalHeight,
                seaLevel,
                ocean,
                parentBiomeKey,
                builtLayers
        );
    }

    private List<HydrologyColumnLayer> resolveSurfaceHeadConflicts(List<HydrologyColumnLayer> candidates) {
        HashMap<Long, HydrologyColumnLayer> selectedByCourse = new HashMap<>();
        for (HydrologyColumnLayer candidate : candidates) {
            if (!conflictingSurfaceCandidate(candidate)) {
                continue;
            }
            long courseId = candidate.feature().courseId();
            HydrologyColumnLayer selected = selectedByCourse.get(courseId);
            if (selected == null || prefersSurfaceLayer(candidate, selected)) {
                selectedByCourse.put(courseId, candidate);
            }
        }
        if (selectedByCourse.isEmpty()) {
            return candidates;
        }
        ArrayList<HydrologyColumnLayer> resolved = new ArrayList<>(candidates.size());
        for (HydrologyColumnLayer candidate : candidates) {
            HydrologyColumnLayer selected = selectedByCourse.get(candidate.feature().courseId());
            if (!conflictingSurfaceCandidate(candidate)
                    || selected == null
                    || candidate.fluidHeadY() == selected.fluidHeadY()) {
                resolved.add(candidate);
            }
        }
        return List.copyOf(resolved);
    }

    private boolean conflictingSurfaceCandidate(HydrologyColumnLayer layer) {
        return layer.feature().type().isSurface()
                && layer.channel()
                && layer.connectedFluid()
                && layer.fluidOwned()
                && !layer.fallingFluid();
    }

    private boolean prefersSurfaceLayer(HydrologyColumnLayer candidate, HydrologyColumnLayer selected) {
        if (candidate.receivingPool() != selected.receivingPool()) {
            return candidate.receivingPool();
        }
        if (candidate.fluidHeadY() != selected.fluidHeadY()) {
            return candidate.fluidHeadY() > selected.fluidHeadY();
        }
        return candidate.feature().id() < selected.feature().id();
    }

    void merge(HydrologyColumnSample sample) {
        if (sample.x() != x
                || sample.z() != z
                || sample.naturalHeight() != naturalHeight
                || sample.seaLevel() != seaLevel
                || sample.ocean() != ocean
                || !sample.parentBiomeKey().equals(parentBiomeKey)) {
            throw new IllegalStateException("Hydrology course footprints disagree on terrain metadata at "
                    + x + "," + z + ".");
        }
        for (HydrologyColumnLayer layer : sample.layers()) {
            add(layer);
        }
    }

    private HydrologyColumnLayer merge(HydrologyColumnLayer first, HydrologyColumnLayer second) {
        if (first.equals(second)) {
            return first;
        }
        HydrologyFeatureRef feature = first.feature().y() <= second.feature().y()
                ? first.feature()
                : second.feature();
        boolean drySurfaceBlend = first.feature().type().isSurface()
                && second.feature().type().isSurface()
                && !first.channel()
                && !second.channel();
        int bedY = drySurfaceBlend
                ? Math.max(first.bedY(), second.bedY())
                : Math.min(first.bedY(), second.bedY());
        return new HydrologyColumnLayer(
                feature,
                bedY,
                Math.max(first.fluidHeadY(), second.fluidHeadY()),
                Math.max(first.ceilingY(), second.ceilingY()),
                first.channel() || second.channel(),
                first.shore() || second.shore(),
                first.grading() || second.grading(),
                first.connectedFluid() || second.connectedFluid(),
                first.fallingFluid() || second.fallingFluid(),
                first.receivingPool() || second.receivingPool(),
                (first.terrainOwned() || second.terrainOwned()) && (!drySurfaceBlend || bedY < naturalHeight),
                first.fluidOwned() || second.fluidOwned(),
                first.oceanApron() || second.oceanApron(),
                first.profileKey(),
                first.surfaceBiomeKey(),
                first.mouthBiomeKey(),
                first.shoreBiomeKey(),
                first.bankBiomeKey(),
                first.floodedCaveBiomeKey()
        );
    }
}
