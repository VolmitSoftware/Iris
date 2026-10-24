package com.volmit.host;

import com.volmit.host.schema.ValueKind;
import com.volmit.host.schema.ValueKind.ObjectKind;
import com.volmit.resource.ResourceId;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.MalformedParameterizedTypeException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Describes every class reachable from the settings and resource kinds of a pack, each once, except host types,
 * which the host describes itself. A class is recorded before
 * its fields are described, so a class that contains itself, directly or through others, ends the
 * walk at its own name. Each class is instantiated once with its no-argument constructor, the way the
 * host creates settings before decoding JSON into them, and its fields' values there are the defaults.
 */
final class TypeDrafts {
    private final Map<Class<?>, TypeDraft> drafts = new LinkedHashMap<>();
    private final Set<Class<?>> describing = new HashSet<>();
    private final Map<String, Class<?>> names = new HashMap<>();
    private final HostTypes hostTypes;
    private final Function<Class<?>, Optional<ResourceId>> resourceKinds;

    /** @param resourceKinds the id of the resource kind read into a class, which {@code @ResourceRef} options name */
    TypeDrafts(HostTypes hostTypes, Function<Class<?>, Optional<ResourceId>> resourceKinds) {
        this.hostTypes = hostTypes;
        this.resourceKinds = resourceKinds;
    }

    /** One described class: its metadata and its options with their kinds and raw default values. */
    record TypeDraft(Class<?> type, SchemaSource source, OptionsMetadata metadata, List<OptionDraft> options) {}

    /** {@code defaultValue} is the field's value in a new instance, or null. */
    record OptionDraft(OptionsMetadata.Option option, ValueKind kind, Object defaultValue) {}

    /** Describes a catalogued settings type and everything it reaches. */
    ObjectKind settings(SchemaSource source) {
        return root("Settings type", source);
    }

    /** Describes the class a resource kind reads its files into and everything it reaches. */
    ObjectKind resourceKind(SchemaSource source) {
        return root("Resource type", source);
    }

    private ObjectKind root(String label, SchemaSource source) {
        var type = source.type();
        try {
            return object(type, source);
        } catch (SettingsTypeProblem problem) {
            throw new IllegalArgumentException(label + " " + type.getName() + " of " + source + " " + problem.getMessage());
        }
    }

    Collection<TypeDraft> drafts() {
        return drafts.values();
    }

    private ObjectKind object(Class<?> type, SchemaSource source) {
        claimName(type, source);
        if (drafts.containsKey(type) || describing.contains(type)) {
            return new ObjectKind(type.getName());
        }
        requireDescribable(type);
        describing.add(type);
        drafts.put(type, draft(type, source));
        describing.remove(type);
        return new ObjectKind(type.getName());
    }

    private TypeDraft draft(Class<?> type, SchemaSource source) {
        var metadata = inspect(type, source);
        var instance = instantiate(type, source);
        var kinds = new ValueKinds(nested -> object(nested, source), hostTypes, resourceKinds,
                source.module().source() == DiscoveredModule.Source.HOST);
        var options = metadata.options().stream()
                .map(option -> new OptionDraft(option, kind(option, kinds, source), defaultValue(option.field(), instance, source)))
                .toList();
        return new TypeDraft(type, source, metadata, options);
    }

    private void claimName(Class<?> type, SchemaSource source) {
        var previous = names.putIfAbsent(type.getName(), type);
        if (previous == null || previous == type) return;
        throw new IllegalArgumentException("Two different classes named " + type.getName() + " are used by settings,"
                + " the second by " + source + "; studio describes classes by name, so rename one");
    }

    private static void requireDescribable(Class<?> type) {
        if (type.isPrimitive()) throw unsupported(type, "which settings cannot use; " + ValueKinds.SUPPORTED_TYPES);
        if (isJdkClass(type)) throw unsupported(type, "a JDK class settings cannot use; " + ValueKinds.SUPPORTED_TYPES);
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw unsupported(type, "which is abstract; settings fields need a concrete class");
        }
        if (type.isAnonymousClass() || type.isLocalClass()) {
            throw unsupported(type, "which is a local or anonymous class; declare it as a top-level or static nested class");
        }
        if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers())) {
            throw unsupported(type, "which is an inner class; declare it static");
        }
    }

    /** JDK classes live in named modules of the boot layer; module classes, built-in or from packs, do not. */
    private static boolean isJdkClass(Class<?> type) {
        var module = type.getModule();
        return module.isNamed() && module.getLayer() == ModuleLayer.boot();
    }

    private static OptionsMetadata inspect(Class<?> type, SchemaSource source) {
        try {
            return OptionsMetadata.inspect(type);
        } catch (IllegalArgumentException | LinkageError failure) {
            throw new IllegalArgumentException("Class " + type.getName() + " of " + source
                    + " cannot be described: " + failure.getMessage(), failure);
        }
    }

    private static Object instantiate(Class<?> type, SchemaSource source) {
        var constructor = noArgumentConstructor(type, source);
        try {
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (InvocationTargetException failure) {
            throw new IllegalArgumentException("The no-argument constructor of " + type.getName() + " of " + source
                    + " threw " + failure.getCause() + "; the host creates settings with it, so it must not fail",
                    failure.getCause());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            throw new IllegalArgumentException("Class " + type.getName() + " of " + source
                    + " cannot be created with its no-argument constructor: " + failure, failure);
        }
    }

    private static Constructor<?> noArgumentConstructor(Class<?> type, SchemaSource source) {
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException missing) {
            throw new IllegalArgumentException("Class " + type.getName() + " of " + source + " has no no-argument"
                    + " constructor; the host creates settings with it and reads their defaults from it, so declare one,"
                    + " private if nothing else should use it");
        }
    }

    private static ValueKind kind(OptionsMetadata.Option option, ValueKinds kinds, SchemaSource source) {
        try {
            return kinds.of(option);
        } catch (SettingsTypeProblem problem) {
            throw new IllegalArgumentException("Field " + fieldName(option.field()) + " of " + source + " "
                    + problem.getMessage());
        } catch (TypeNotPresentException | MalformedParameterizedTypeException | LinkageError failure) {
            throw new IllegalArgumentException("Field " + fieldName(option.field()) + " of " + source
                    + " uses a type that cannot be loaded: " + failure, failure);
        }
    }

    private static Object defaultValue(Field field, Object instance, SchemaSource source) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (IllegalAccessException | InaccessibleObjectException failure) {
            throw new IllegalArgumentException("Field " + fieldName(field) + " of " + source
                    + " cannot be read for its default: " + failure, failure);
        }
    }

    private static String fieldName(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    private static SettingsTypeProblem unsupported(Class<?> type, String why) {
        return new SettingsTypeProblem("uses " + type.getName() + ", " + why);
    }
}
