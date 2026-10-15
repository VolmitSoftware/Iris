package com.volmit.host;

import com.volmit.block.BlockState;
import com.volmit.config.Desc;
import com.volmit.config.OptionsMetadata;
import com.volmit.config.Reference;
import com.volmit.host.schema.TextFormat;
import com.volmit.host.schema.ValueKind;
import com.volmit.host.schema.ValueKind.AnyKind;
import com.volmit.host.schema.ValueKind.BooleanKind;
import com.volmit.host.schema.ValueKind.DecimalKind;
import com.volmit.host.schema.ValueKind.EnumConstant;
import com.volmit.host.schema.ValueKind.EnumKind;
import com.volmit.host.schema.ValueKind.HostKind;
import com.volmit.host.schema.ValueKind.IntegerKind;
import com.volmit.host.schema.ValueKind.ListKind;
import com.volmit.host.schema.ValueKind.MapKind;
import com.volmit.host.schema.ValueKind.ObjectKind;
import com.volmit.host.schema.ValueKind.ReferenceKind;
import com.volmit.host.schema.ValueKind.ResourceReferenceKind;
import com.volmit.host.schema.ValueKind.TextKind;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKey;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Maps a settings field's declared type, {@code @Ref} and {@code @ResourceRef} to the JSON it takes. Host types
 * become host kinds where the settings may use them; other classes that are none of the built-in kinds become
 * object kinds through the given function. Throws {@link SettingsTypeProblem} for a type or reference that does
 * not fit.
 */
final class ValueKinds {
    static final String SUPPORTED_TYPES = "settings fields can be booleans, numbers, String, ResourceId, BlockState, enums, "
            + "lists, sets and arrays of these, Map with String keys, Object for any JSON value and classes with a no-argument constructor";
    private static final Set<Class<?>> BOOLEANS = Set.of(boolean.class, Boolean.class);
    private static final Set<Class<?>> INTEGERS = Set.of(byte.class, short.class, int.class, long.class,
            Byte.class, Short.class, Integer.class, Long.class);
    private static final Set<Class<?>> DECIMALS = Set.of(float.class, double.class, Float.class, Double.class);
    private static final String DECLARE_COLLECTION = "declare it as List<...>, Set<...> or Map<String, ...>";
    private static final Set<Reference> PACK_REFERENCES = EnumSet.of(Reference.PACK_BIOME, Reference.PACK_REGION, Reference.PACK_OBJECT,
            Reference.PACK_EXPRESSION, Reference.PACK_IMAGE_MAP, Reference.PACK_STRUCTURE);

    private final Function<Class<?>, ObjectKind> objects;
    private final HostTypes hostTypes;
    private final Function<Class<?>, Optional<ResourceId>> resourceKinds;
    private final boolean hostTypesAllowed;

    /** {@code resourceKinds} gives the id of the resource kind read into a class; {@code hostTypesAllowed} is
     * whether the settings belong to a module that ships with the host.
     */
    ValueKinds(Function<Class<?>, ObjectKind> objects, HostTypes hostTypes, Function<Class<?>, Optional<ResourceId>> resourceKinds,
               boolean hostTypesAllowed) {
        this.objects = objects;
        this.hostTypes = hostTypes;
        this.resourceKinds = resourceKinds;
        this.hostTypesAllowed = hostTypesAllowed;
    }

    ValueKind of(OptionsMetadata.Option option) {
        return kind(option.type(), new Naming(option.reference(), option.resource()));
    }

    /** What a text option names: the field's {@code @Ref} and the class of its {@code @ResourceRef}, each null when absent. */
    private record Naming(Reference reference, Class<?> resource) {
        static final Naming NONE = new Naming(null, null);

        boolean isEmpty() {
            return reference == null && resource == null;
        }
    }

    /** Lists, sets and arrays pass {@code naming} on to their elements. */
    private ValueKind kind(Type type, Naming naming) {
        return switch (type) {
            case Class<?> plain -> classKind(plain, naming);
            case ParameterizedType generic -> genericKind(generic, naming);
            case GenericArrayType array -> new ListKind(kind(array.getGenericComponentType(), naming), false);
            default -> throw unsupported(type, ", which is not a concrete type; name the type, such as List<String>");
        };
    }

    private ValueKind classKind(Class<?> type, Naming naming) {
        if (hostTypes.contains(type)) return hostKind(type, naming);
        if (type == String.class) return text(TextFormat.PLAIN, naming);
        if (type == ResourceId.class) return text(TextFormat.RESOURCE_ID, naming);
        if (type == BlockState.class) return blockState(naming);
        if (type.isArray()) return new ListKind(kind(type.getComponentType(), naming), false);
        if (isCollection(type)) throw unsupported(type, " without type arguments; " + DECLARE_COLLECTION);
        if (type == ResourceKey.class) throw resourceKey(type);
        requireNoNaming(type, naming);
        if (BOOLEANS.contains(type)) return new BooleanKind();
        if (INTEGERS.contains(type)) return new IntegerKind();
        if (DECIMALS.contains(type)) return new DecimalKind();
        if (type.isEnum()) return enumKind(type);
        if (type == Object.class) return new AnyKind();
        return objects.apply(type);
    }

    private ValueKind genericKind(ParameterizedType generic, Naming naming) {
        var raw = (Class<?>) generic.getRawType();
        if (hostTypes.contains(raw)) return hostKind(raw, naming);
        var arguments = generic.getActualTypeArguments();
        if (raw == List.class || raw == Set.class) return new ListKind(kind(arguments[0], naming), raw == Set.class);
        if (raw == Map.class) return mapKind(generic, arguments, naming);
        if (isCollection(raw)) throw unsupported(generic, "; " + DECLARE_COLLECTION);
        if (raw == ResourceKey.class) throw resourceKey(generic);
        throw unsupported(generic, "; only List, Set and Map may have type arguments");
    }

    private ValueKind mapKind(ParameterizedType generic, Type[] arguments, Naming naming) {
        requireNoNaming(generic, naming);
        if (arguments[0] != String.class) throw unsupported(generic, "; map keys must be String");
        return new MapKind(kind(arguments[1], Naming.NONE));
    }

    private HostKind hostKind(Class<?> type, Naming naming) {
        requireNoNaming(type, naming);
        if (hostTypesAllowed) return new HostKind(type.getName());
        throw new SettingsTypeProblem("uses " + type.getName() + ", a class the host describes itself, which only settings of"
                + " modules that ship with the host may use; " + SUPPORTED_TYPES);
    }

    private ValueKind text(TextFormat format, Naming naming) {
        if (naming.resource() != null) return resourceReference(format, naming);
        var reference = naming.reference();
        if (reference == null) return new TextKind(format);
        if (format == TextFormat.RESOURCE_ID && PACK_REFERENCES.contains(reference)) throw packReferenceOnResourceId(reference);
        return new ReferenceKind(reference, format);
    }

    /** Packs name a kind's resources by key without a namespace, as they name their biomes, so only plain text holds one. */
    private ValueKind resourceReference(TextFormat format, Naming naming) {
        var resource = naming.resource();
        if (naming.reference() != null) {
            throw new SettingsTypeProblem("has both @Ref(" + naming.reference() + ") and @ResourceRef(" + resource.getName()
                    + "); an option names one kind of thing, so keep one");
        }
        if (format != TextFormat.PLAIN) {
            throw new SettingsTypeProblem("has @ResourceRef(" + resource.getName() + ") on ResourceId, but packs name a kind's"
                    + " resources by key without a namespace, such as castle/start; use String");
        }
        return resourceKinds.apply(resource).map(ResourceReferenceKind::new).orElseThrow(() -> new SettingsTypeProblem(
                "has @ResourceRef(" + resource.getName() + "), but no module of the pack registers a resource kind read into"
                        + " that class; name the class of a kind registered with FeatureRegistry.registerResourceKind"));
    }

    /** Packs name their biomes, regions and objects by path without a namespace, so a ResourceId could not hold one. */
    private static SettingsTypeProblem packReferenceOnResourceId(Reference reference) {
        return new SettingsTypeProblem("has @Ref(" + reference + ") on ResourceId, but packs name their biomes,"
                + " regions and objects by path without a namespace, such as desert/dunes; use String");
    }

    private static ValueKind blockState(Naming naming) {
        var reference = naming.reference();
        if (naming.resource() == null && (reference == null || reference == Reference.BLOCK)) {
            return new ReferenceKind(Reference.BLOCK, TextFormat.BLOCK_STATE);
        }
        if (naming.resource() != null) {
            throw new SettingsTypeProblem("has @ResourceRef(" + naming.resource().getName() + ") on BlockState, which always"
                    + " names a block; remove the @ResourceRef or use String");
        }
        throw new SettingsTypeProblem("has @Ref(" + reference + ") on BlockState, which always names a block;"
                + " remove the @Ref or use String or ResourceId");
    }

    private static EnumKind enumKind(Class<?> type) {
        var constants = Arrays.stream(type.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name())
                .map(name -> new EnumConstant(name, constantDescription(type, name)))
                .toList();
        return new EnumKind(type.getName(), constants);
    }

    private static String constantDescription(Class<?> type, String constant) {
        try {
            var desc = type.getField(constant).getAnnotation(Desc.class);
            return desc == null ? "" : desc.value();
        } catch (NoSuchFieldException impossible) {
            throw new IllegalStateException("Enum constant without field: " + type.getName() + "." + constant, impossible);
        }
    }

    private static void requireNoNaming(Type type, Naming naming) {
        if (naming.isEmpty()) return;
        if (naming.reference() != null) {
            throw new SettingsTypeProblem("has @Ref(" + naming.reference() + ") on " + type.getTypeName()
                    + ", but @Ref fits only String and ResourceId and lists, sets and arrays of them");
        }
        throw new SettingsTypeProblem("has @ResourceRef(" + naming.resource().getName() + ") on " + type.getTypeName()
                + ", but @ResourceRef fits only String and lists, sets and arrays of it");
    }

    /** ResourceKey's type argument is the Java class a resource loads as, which tells studio nothing to list. */
    private static SettingsTypeProblem resourceKey(Type type) {
        return unsupported(type, ", which names a Java type, not something studio can list; use ResourceId,"
                + " with @Ref when it names a block, biome or other listed entry");
    }

    private static boolean isCollection(Class<?> type) {
        return Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type);
    }

    /** {@code why} starts with its own separator, such as ", which ..." or "; ...". */
    private static SettingsTypeProblem unsupported(Type type, String why) {
        return new SettingsTypeProblem("uses " + type.getTypeName() + why);
    }
}
