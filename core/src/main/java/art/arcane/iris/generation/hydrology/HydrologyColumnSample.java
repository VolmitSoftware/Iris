package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record HydrologyColumnSample(
        int x,
        int z,
        int naturalHeight,
        int seaLevel,
        boolean ocean,
        String parentBiomeKey,
        List<HydrologyColumnLayer> layers
) {
    private static final Comparator<HydrologyColumnLayer> LAYER_ORDER = Comparator
            .comparingInt((HydrologyColumnLayer layer) -> layer.feature().type().renderPriority())
            .thenComparing(Comparator.comparingInt(HydrologyColumnLayer::fluidHeadY).reversed())
            .thenComparingLong((HydrologyColumnLayer layer) -> layer.feature().id());
    private static final Comparator<HydrologyColumnLayer> SURFACE_LAYER_ORDER = Comparator
            .comparingInt(HydrologyColumnSample::surfaceRolePriority)
            .thenComparingInt((HydrologyColumnLayer layer) -> layer.feature().type().renderPriority())
            .thenComparing(Comparator.comparingInt(HydrologyColumnLayer::fluidHeadY).reversed())
            .thenComparingInt(HydrologyColumnLayer::bedY)
            .thenComparingLong((HydrologyColumnLayer layer) -> layer.feature().id());

    public HydrologyColumnSample {
        if (parentBiomeKey == null || parentBiomeKey.isBlank()) {
            throw new IllegalArgumentException("parentBiomeKey must not be blank.");
        }
        parentBiomeKey = parentBiomeKey.trim();
        Objects.requireNonNull(layers, "layers");
        if (layers.size() < 2) {
            layers = List.copyOf(layers);
        } else {
            ArrayList<HydrologyColumnLayer> ordered = new ArrayList<>(layers);
            ordered.sort(LAYER_ORDER);
            layers = List.copyOf(ordered);
        }
        if (ocean) {
            for (HydrologyColumnLayer layer : layers) {
                if (layer.terrainOwned() || layer.fluidOwned() || layer.grading() || layer.shore()) {
                    throw new IllegalArgumentException("Ocean columns cannot contain river-owned writes or grading.");
                }
                if (layer.fluidHeadY() > seaLevel) {
                    throw new IllegalArgumentException("Ocean column fluid cannot be elevated above sea level.");
                }
            }
        }
        if (naturalHeight <= seaLevel) {
            for (HydrologyColumnLayer layer : layers) {
                if (layer.feature().type().isSurface()
                        && !layer.oceanApron()
                        && (layer.terrainOwned() || layer.fluidOwned() || layer.grading() || layer.shore())
                        && (naturalHeight < seaLevel || layer.fluidHeadY() > seaLevel)) {
                    throw new IllegalArgumentException(
                            "Surface hydrology cannot own submerged ground or raise sea-level water."
                    );
                }
            }
        }
    }

    public boolean present() {
        return !layers.isEmpty();
    }

    public boolean hasFeature(HydrologyFeatureType type) {
        Objects.requireNonNull(type, "type");
        for (HydrologyColumnLayer layer : layers) {
            if (layer.feature().type() == type) {
                return true;
            }
        }
        return false;
    }

    public boolean hasConnectedFluid() {
        for (HydrologyColumnLayer layer : layers) {
            if (layer.connectedFluid()) {
                return true;
            }
        }
        return false;
    }

    public Optional<HydrologyColumnLayer> primaryLayer() {
        return layers.isEmpty() ? Optional.empty() : Optional.of(layers.getFirst());
    }

    public Optional<HydrologyColumnLayer> primarySurfaceLayer() {
        return Optional.ofNullable(selectSurfaceLayer(false));
    }

    public Optional<HydrologyColumnLayer> primarySurfaceFluidLayer() {
        return Optional.ofNullable(selectSurfaceLayer(true));
    }

    public HydrologyColumnLayer primarySurfaceLayerOrNull() {
        return selectSurfaceLayer(false);
    }

    public HydrologyColumnLayer primarySurfaceFluidLayerOrNull() {
        return selectSurfaceLayer(true);
    }

    public Optional<SurfacePublicationCell> surfacePublicationCellAt(int y) {
        if (y <= terrainHeight()) {
            return Optional.empty();
        }
        SurfacePublicationCell selected = null;
        for (HydrologyColumnLayer layer : layers) {
            if (!layer.publishesSurfaceFluid() || y <= layer.bedY() || y > layer.fluidHeadY()) {
                continue;
            }
            HydrologyCaveAction action = layer.fallingFluid() && y < layer.fluidHeadY()
                    ? HydrologyCaveAction.FALLING_FLUID
                    : HydrologyCaveAction.WET_SOURCE;
            SurfacePublicationCell candidate = new SurfacePublicationCell(layer, action);
            if (selected == null || surfaceActionPriority(action) < surfaceActionPriority(selected.action())) {
                selected = candidate;
            }
        }
        return Optional.ofNullable(selected);
    }

    public int terrainHeight() {
        HydrologyColumnLayer primary = selectSurfaceLayer(false);
        if (primary == null || !primary.terrainOwned()) {
            return naturalHeight;
        }
        if (primary.channel()) {
            return primary.bedY();
        }
        int terrainHeight = primary.bedY();
        for (HydrologyColumnLayer candidate : layers) {
            if (candidate.feature().type().isSurface()
                    && candidate.terrainOwned()
                    && !candidate.channel()) {
                terrainHeight = Math.max(terrainHeight, candidate.bedY());
            }
        }
        return terrainHeight;
    }

    public HydrologyRenderSample renderSample() {
        ArrayList<HydrologyFeatureRef> features = new ArrayList<>(layers.size());
        for (HydrologyColumnLayer layer : layers) {
            features.add(layer.feature());
        }
        return new HydrologyRenderSample(x, z, features);
    }

    private HydrologyColumnLayer selectSurfaceLayer(boolean fluidOnly) {
        HydrologyColumnLayer selected = null;
        for (HydrologyColumnLayer layer : layers) {
            if (layer.oceanApron() || !layer.feature().type().isSurface()) {
                continue;
            }
            if (fluidOnly) {
                if (!layer.channel() || !layer.connectedFluid() || !layer.fluidOwned()) {
                    continue;
                }
            }
            if (selected == null || SURFACE_LAYER_ORDER.compare(layer, selected) < 0) {
                selected = layer;
            }
        }
        return selected;
    }

    private static int surfaceRolePriority(HydrologyColumnLayer layer) {
        if (layer.channel()) {
            return 0;
        }
        if (layer.terrainOwned()) {
            return layer.shore() ? 1 : 2;
        }
        return layer.shore() ? 3 : 4;
    }

    private static int surfaceActionPriority(HydrologyCaveAction action) {
        return action == HydrologyCaveAction.WET_SOURCE ? 0 : 1;
    }

    public record SurfacePublicationCell(
            HydrologyColumnLayer layer,
            HydrologyCaveAction action
    ) {
        public SurfacePublicationCell {
            Objects.requireNonNull(layer, "layer");
            if (action != HydrologyCaveAction.WET_SOURCE
                    && action != HydrologyCaveAction.FALLING_FLUID) {
                throw new IllegalArgumentException("Surface publication cells must contain fluid.");
            }
        }
    }
}
