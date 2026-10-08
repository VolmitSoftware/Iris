package com.volmit.host;

import com.volmit.block.BlockState;
import com.volmit.config.Desc;
import com.volmit.config.OptionsMetadata;
import com.volmit.config.Reference;
import com.volmit.host.schema.TextFormat;
import com.volmit.host.schema.ValueKind;
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
import java.util.Set;
import java.util.function.Function;

/** Maps a settings field's declared type and {@code @Ref} to the JSON it takes. Host types become host
 * kinds where the settings may use them; other classes that are none of the built-in kinds become object
 * kinds through the given function. Throws {@link SettingsTypeProblem} for a type or reference that does
 * not fit.
 */
final class ValueKinds {
    static final String SUPPORTED_TYPES = "settings fields can be booleans, numbers, String, ResourceId, BlockState, enums, "
            + "lists, sets and arrays of these, Map with String keys and classes with a no-argument constructor";
    private static final Set<Class<?>> BOOLEANS = Set.of(boolean.class, Boolean.class);
    private static final Set<Class<?>> INTEGERS = Set.of(byte.class, short.class, int.class, long.class,
            Byte.class, Short.class, Integer.class, Long.class);
    private static final Set<Class<?>> DECIMALS = Set.of(float.class, double.class, Float.class, Double.class);
    private static final String DECLARE_COLLECTION = "declare it as List<...>, Set<...> or Map<String, ...>";
    private static final Set<Reference> PACK_REFERENCES = EnumSet.of(Reference.PACK_BIOME, Reference.PACK_REGION, Reference.PACK_OBJECT);

    private final Function<Class<?>, ObjectKind> objects;
    private final HostTypes hostTypes;
    private final boolean hostTypesAllowed;

    /** {@code hostTypesAllowed} is whether the settings belong to a module that ships with the host. */
    ValueKinds(Function<Class<?>, ObjectKind> objects, HostTypes hostTypes, boolean hostTypesAllowed) {
        this.objects = objects;
        this.hostTypes = hostTypes;
        this.hostTypesAllowed = hostTypesAllowed;
    }

    ValueKind of(OptionsMetadata.Option option) {
        return kind(option.type(), option.reference());
    }

    /** {@code reference} is the field's {@code @Ref}, or null; lists, sets and arrays pass it to their elements. */
    private ValueKind kind(Type type, Reference reference) {
        return switch (type) {
            case Class<?> plain -> classKind(plain, reference);
            case ParameterizedType generic -> genericKind(generic, reference);
            case GenericArrayType array -> new ListKind(kind(array.getGenericComponentType(), reference), false);
            default -> throw unsupported(type, ", which is not a concrete type; name the type, such as List<String>");
        };
    }

    private ValueKind classKind(Class<?> type, Reference reference) {
        if (hostTypes.contains(type)) return hostKind(type, reference);
        if (type == String.class) return text(TextFormat.PLAIN, reference);
        if (type == ResourceId.class) return text(TextFormat.RESOURCE_ID, reference);
        if (type == BlockState.class) return blockState(reference);
        if (type.isArray()) return new ListKind(kind(type.getComponentType(), reference), false);
        if (isCollection(type)) throw unsupported(type, " without type arguments; " + DECLARE_COLLECTION);
        if (type == ResourceKey.class) throw resourceKey(type);
        requireNoReference(type, reference);
        if (BOOLEANS.contains(type)) return new BooleanKind();
        if (INTEGERS.contains(type)) return new IntegerKind();
        if (DECIMALS.contains(type)) return new DecimalKind();
        if (type.isEnum()) return enumKind(type);
        return objects.apply(type);
    }

    private ValueKind genericKind(ParameterizedType generic, Reference reference) {
        var raw = (Class<?>) generic.getRawType();
        if (hostTypes.contains(raw)) return hostKind(raw, reference);
        var arguments = generic.getActualTypeArguments();
        if (raw == List.class || raw == Set.class) return new ListKind(kind(arguments[0], reference), raw == Set.class);
        if (raw == Map.class) return mapKind(generic, arguments, reference);
        if (isCollection(raw)) throw unsupported(generic, "; " + DECLARE_COLLECTION);
        if (raw == ResourceKey.class) throw resourceKey(generic);
        throw unsupported(generic, "; only List, Set and Map may have type arguments");
    }

    private ValueKind mapKind(ParameterizedType generic, Type[] arguments, Reference reference) {
        requireNoReference(generic, reference);
        if (arguments[0] != String.class) throw unsupported(generic, "; map keys must be String");
        return new MapKind(kind(arguments[1], null));
    }

    private HostKind hostKind(Class<?> type, Reference reference) {
        requireNoReference(type, reference);
        if (hostTypesAllowed) return new HostKind(type.getName());
        throw new SettingsTypeProblem("uses " + type.getName() + ", a class the host describes itself, which only settings of"
                + " modules that ship with the host may use; " + SUPPORTED_TYPES);
    }

    private static ValueKind text(TextFormat format, Reference reference) {
        if (reference == null) return new TextKind(format);
        if (format == TextFormat.RESOURCE_ID && PACK_REFERENCES.contains(reference)) throw packReferenceOnResourceId(reference);
        return new ReferenceKind(reference, format);
    }

    /** Packs name their biomes, regions and objects by path without a namespace, so a ResourceId could not hold one. */
    private static SettingsTypeProblem packReferenceOnResourceId(Reference reference) {
        return new SettingsTypeProblem("has @Ref(" + reference + ") on ResourceId, but packs name their biomes,"
                + " regions and objects by path without a namespace, such as desert/dunes; use String");
    }

    private static ValueKind blockState(Reference reference) {
        if (reference == null || reference == Reference.BLOCK) return new ReferenceKind(Reference.BLOCK, TextFormat.BLOCK_STATE);
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

    private static void requireNoReference(Type type, Reference reference) {
        if (reference == null) return;
        throw new SettingsTypeProblem("has @Ref(" + reference + ") on " + type.getTypeName()
                + ", but @Ref fits only String and ResourceId and lists, sets and arrays of them");
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
