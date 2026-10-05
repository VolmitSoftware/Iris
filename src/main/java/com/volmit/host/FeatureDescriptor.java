package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.OptionsMetadata;
import com.volmit.resource.ResourceId;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Set;

/** Validated settings metadata. It describes configuration, not executable ordering. */
public record FeatureDescriptor<F extends IrisFeature>(
        ResourceId id,
        String name,
        Class<F> type,
        FeatureAttachment attachment,
        OptionsMetadata options) {
    public FeatureDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Blank feature name");
        }
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(options, "options");
    }

    /** Reads the @Feature declaration of a concrete settings class. */
    public static <F extends IrisFeature> FeatureDescriptor<F> of(Class<F> type) {
        Objects.requireNonNull(type, "featureType");
        requireConcreteFeature(type);

        var annotation = type.getDeclaredAnnotation(Feature.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Missing @Feature: " + type.getName());
        }

        return new FeatureDescriptor<>(
                ResourceId.parse(annotation.id()),
                annotation.name(),
                type,
                new FeatureAttachment(annotation.field(), Set.of(annotation.targets())),
                OptionsMetadata.inspect(type));
    }

    private static void requireConcreteFeature(Class<?> type) {
        if (!IrisFeature.class.isAssignableFrom(type)
                || type.isInterface()
                || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException(
                    "Feature type must be a concrete IrisFeature: " + type.getName());
        }
    }
}
