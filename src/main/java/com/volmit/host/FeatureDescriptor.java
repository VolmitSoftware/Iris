package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.Level;
import com.volmit.config.OptionsMetadata;
import com.volmit.resource.ResourceId;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validated settings metadata. It describes configuration, not executable ordering.
 *
 * @param key the JSON key packs write the settings under at each target; see {@link Feature#key}
 * @param root the option whose value the key holds, when the JSON is one option's value instead of an object
 *     of options; see {@link Feature#root}
 * @param targets the levels whose pack files hold the key, at least one
 */
public record FeatureDescriptor<F extends IrisFeature>(
        ResourceId id,
        String name,
        String key,
        Optional<String> root,
        Class<F> type,
        Set<Level> targets,
        OptionsMetadata options) {
    private static final char NAMESPACE_SEPARATOR = ':';

    public FeatureDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Blank feature name");
        }
        requireKey(id, key);
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(type, "type");
        targets = requireTargets(targets);
        Objects.requireNonNull(options, "options");
        root.ifPresent(option -> requireOnlyOption(type, option, options));
    }

    /** Checks a JSON field name that is not a feature id: a settings key or a field the host itself uses.
     * It is not blank and has no ':', which every feature id has.
     */
    public static String requireFieldName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Field must not be blank");
        }
        if (name.indexOf(NAMESPACE_SEPARATOR) >= 0) {
            throw new IllegalArgumentException("Field " + name + " contains ':', which only feature ids may contain");
        }
        return name;
    }

    private static Set<Level> requireTargets(Set<Level> targets) {
        Objects.requireNonNull(targets, "targets");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("At least one target is required");
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(targets));
    }

    /** Reads the @Feature declaration of a concrete settings class. */
    public static <F extends IrisFeature> FeatureDescriptor<F> of(Class<F> type) {
        Objects.requireNonNull(type, "featureType");
        requireConcreteFeature(type);

        var annotation = type.getDeclaredAnnotation(Feature.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Missing @Feature: " + type.getName());
        }

        try {
            return new FeatureDescriptor<>(
                    ResourceId.parse(annotation.id()),
                    annotation.name(),
                    annotation.key(),
                    annotation.root().isEmpty() ? Optional.empty() : Optional.of(annotation.root()),
                    type,
                    Set.of(annotation.targets()),
                    OptionsMetadata.inspect(type));
        } catch (IncompleteAnnotationException missing) {
            throw IncompleteFeatureException.of(type, annotation, missing);
        }
    }

    /** The settings type a value of {@code valueClass} is configured as: the nearest class of its hierarchy that
     * declares {@code @Feature}, so a subclass without its own declaration counts as its declared superclass.
     */
    public static Class<?> declaredType(Class<?> valueClass) {
        return DECLARED_TYPES.get(Objects.requireNonNull(valueClass, "valueClass"));
    }

    private static final ClassValue<Class<?>> DECLARED_TYPES = new ClassValue<>() {
        @Override
        protected Class<?> computeValue(Class<?> valueClass) {
            for (Class<?> type = valueClass; type != null; type = type.getSuperclass()) {
                if (type.getDeclaredAnnotation(Feature.class) != null) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Missing @Feature: " + valueClass.getName() + " and its superclasses declare none");
        }
    };

    private static void requireKey(ResourceId id, String key) {
        try {
            requireFieldName(key);
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
