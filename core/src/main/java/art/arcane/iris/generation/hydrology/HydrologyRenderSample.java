package art.arcane.iris.generation.hydrology;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record HydrologyRenderSample(int x, int z, List<HydrologyFeatureRef> features) {
    private static final Comparator<HydrologyFeatureRef> FEATURE_ORDER = Comparator
            .comparingInt((HydrologyFeatureRef feature) -> feature.type().renderPriority())
            .thenComparingLong(HydrologyFeatureRef::id);

    public HydrologyRenderSample {
        Objects.requireNonNull(features, "features");
        ArrayList<HydrologyFeatureRef> ordered = new ArrayList<>(features);
        ordered.sort(FEATURE_ORDER);
        Map<Long, HydrologyFeatureRef> unique = new LinkedHashMap<>();
        for (HydrologyFeatureRef feature : ordered) {
            unique.putIfAbsent(feature.id(), feature);
        }
        features = List.copyOf(unique.values());
    }

    public boolean present() {
        return !features.isEmpty();
    }

    public boolean hasFeature(HydrologyFeatureType type) {
        Objects.requireNonNull(type, "type");
        for (HydrologyFeatureRef feature : features) {
            if (feature.type() == type) {
                return true;
            }
        }
        return false;
    }

    public Optional<HydrologyFeatureRef> primaryFeature() {
        return features.isEmpty() ? Optional.empty() : Optional.of(features.getFirst());
    }
}
