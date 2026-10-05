package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.generation.ModifierFactory;
import com.volmit.resource.ResourceId;
import java.util.Objects;

public record GenerationRegistration<F extends IrisFeature, T>(
        FeatureDescriptor<F> descriptor,
        Class<T> outputType,
        ModifierFactory<F, T> factory) {
    public GenerationRegistration {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(outputType, "outputType");
        if (outputType.isPrimitive()) {
            throw new IllegalArgumentException("Output requires a reference type");
        }
        Objects.requireNonNull(factory, "factory");
    }

    public ConfiguredModifier<T> configure(ResourceId occurrence, F feature) {
        var configuredFeature = descriptor.type().cast(Objects.requireNonNull(feature, "feature"));
        return new ConfiguredModifier<>(occurrence, outputType, factory.create(configuredFeature));
    }
}
