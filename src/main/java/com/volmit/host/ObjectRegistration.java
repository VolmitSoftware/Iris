package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.object.ObjectGenerator;
import com.volmit.object.ObjectGeneratorFactory;
import java.util.Objects;

public record ObjectRegistration<F extends IrisFeature>(
        FeatureDescriptor<F> descriptor,
        ObjectGeneratorFactory<F> factory) {
    public ObjectRegistration {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(factory, "factory");
    }

    public ObjectGenerator configure(F feature) {
        var configuredFeature = descriptor.type().cast(Objects.requireNonNull(feature, "feature"));
        return Objects.requireNonNull(factory.create(configuredFeature), "generator");
    }
}
