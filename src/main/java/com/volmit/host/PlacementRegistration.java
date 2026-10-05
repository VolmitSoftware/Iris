package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.placement.PlacementModifierFactory;
import com.volmit.resource.ResourceId;
import java.util.Objects;

public record PlacementRegistration<F extends IrisFeature>(
        FeatureDescriptor<F> descriptor,
        PlacementModifierFactory<F> factory) {
    public PlacementRegistration {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(factory, "factory");
    }

    public ConfiguredPlacementModifier configure(ResourceId occurrence, F feature) {
        var configuredFeature = descriptor.type().cast(Objects.requireNonNull(feature, "feature"));
        return new ConfiguredPlacementModifier(occurrence, factory.create(configuredFeature));
    }
}
