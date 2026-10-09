package com.volmit.host;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.OptionsMetadata;
import com.volmit.resource.ResourceId;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validated settings metadata. It describes configuration, not executable ordering.
 *
 * @param key the JSON key packs write the settings under at each target; see {@link Feature#key}
 * @param root the option whose value the key holds, when the JSON is one option's value instead of an object
 *     of options; see {@link Feature#root}
 */
public record FeatureDescriptor<F extends IrisFeature>(
        ResourceId id,
        String name,
        String key,
        Optional<String> root,
        Class<F> type,
        FeatureAttachment attachment,
        OptionsMetadata options) {
    public FeatureDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Blank feature name");
        }
        requireKey(id, key);
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(attachment, "attachment");
        Objects.requireNonNull(options, "options");
        root.ifPresent(option -> requireOnlyOption(type, option, options));
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
                annotation.key(),
                annotation.root().isEmpty() ? Optional.empty() : Optional.of(annotation.root()),
                type,
                new FeatureAttachment(Set.of(annotation.targets())),
                OptionsMetadata.inspect(type));
    }

    private static void requireKey(ResourceId id, String key) {
        try {
            FeatureAttachment.requireFieldName(key);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid key of feature " + id + ": " + invalid.getMessage()
                    + "; a key is a plain JSON field name such as \"deposits\"", invalid);
        }
    }

    private static void requireOnlyOption(Class<?> type, String root, OptionsMetadata options) {
        var names = options.options().stream().map(OptionsMetadata.Option::name).toList();
        if (names.equals(List.of(root))) {
            return;
        }
        throw new IllegalArgumentException("Feature root '" + root + "' of " + type.getName() + " must name its only option,"
                + " since the JSON under its key is that option's value; its options are " + names);
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
