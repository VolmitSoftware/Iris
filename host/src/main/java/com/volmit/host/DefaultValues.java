package com.volmit.host;

import com.volmit.block.BlockState;
import com.volmit.host.schema.ValueKind;
import com.volmit.host.schema.ValueKind.AnyKind;
import com.volmit.host.schema.ValueKind.BooleanKind;
import com.volmit.host.schema.ValueKind.DecimalKind;
import com.volmit.host.schema.ValueKind.EnumKind;
import com.volmit.host.schema.ValueKind.HostKind;
import com.volmit.host.schema.ValueKind.IntegerKind;
import com.volmit.host.schema.ValueKind.ListKind;
import com.volmit.host.schema.ValueKind.MapKind;
import com.volmit.host.schema.ValueKind.ObjectKind;
import com.volmit.host.schema.ValueKind.ReferenceKind;
import com.volmit.host.schema.ValueKind.ResourceReferenceKind;
import com.volmit.host.schema.ValueKind.TextKind;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Turns a field's default value into the JSON-shaped value {@link com.volmit.host.schema.SchemaOption}
 * documents, or empty when it has none. Objects are read through the options of their described
 * class, so a subclass instance shows only the declared class's options.
 */
@SuppressWarnings("removal") // host kinds are transitional, until the cut-over
final class DefaultValues {
    private final Map<String, TypeDrafts.TypeDraft> types;
    private final Set<Object> converting = Collections.newSetFromMap(new IdentityHashMap<>());

    DefaultValues(Map<String, TypeDrafts.TypeDraft> types) {
        this.types = types;
    }

    Optional<Object> of(Object value, ValueKind kind) {
        if (value == null) return Optional.empty();
        return switch (kind) {
            case BooleanKind _ -> Optional.of(value);
            case IntegerKind _ -> Optional.of(((Number) value).longValue());
            case DecimalKind _ -> decimal((Number) value);
            case TextKind _, ReferenceKind _, ResourceReferenceKind _ -> Optional.of(text(value));
            case EnumKind _ -> Optional.of(((Enum<?>) value).name());
            case ListKind list -> list(elements(value), list);
            case MapKind map -> map((Map<?, ?>) value, map.value());
            case ObjectKind object -> object(value, types.get(object.type()));
            case HostKind _, AnyKind _ -> Optional.empty();
        };
    }

    /** A float keeps its own shortest decimal form: 0.1f is 0.1, not 0.10000000149011612. */
    private static Optional<Object> decimal(Number value) {
        double converted = value instanceof Float single ? Double.parseDouble(Float.toString(single)) : value.doubleValue();
        if (!Double.isFinite(converted)) return Optional.empty();
        return Optional.of(converted);
    }

    private static String text(Object value) {
        if (value instanceof BlockState state) return state.key();
        return value.toString();
    }

    private static List<?> elements(Object value) {
        if (value instanceof Collection<?> collection) return new ArrayList<>(collection);
        var elements = new ArrayList<>();
        for (int index = 0; index < Array.getLength(value); index++) {
            elements.add(Array.get(value, index));
        }
        return elements;
    }

    private Optional<Object> list(List<?> elements, ListKind kind) {
        var converted = new ArrayList<Object>();
        for (var element : elements) {
            var value = of(element, kind.element());
            if (value.isEmpty()) return Optional.empty();
            converted.add(value.get());
        }
        if (kind.unique()) return sortedSet(converted);
        return Optional.of(List.copyOf(converted));
    }

    /** Set elements are all of one kind; only scalar ones have a natural order to sort by. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Optional<Object> sortedSet(List<Object> elements) {
        if (!elements.stream().allMatch(element -> element instanceof Comparable)) return Optional.empty();
        elements.sort((left, right) -> ((Comparable) left).compareTo(right));
        return Optional.of(List.copyOf(elements));
    }

    private Optional<Object> map(Map<?, ?> entries, ValueKind valueKind) {
        var converted = new TreeMap<String, Object>();
        for (var entry : entries.entrySet()) {
            var value = of(entry.getValue(), valueKind);
            if (entry.getKey() == null || value.isEmpty()) return Optional.empty();
            converted.put((String) entry.getKey(), value.get());
        }
        return Optional.of(Collections.unmodifiableMap(converted));
    }

    private Optional<Object> object(Object value, TypeDrafts.TypeDraft type) {
        if (!converting.add(value)) return Optional.empty();
        var converted = new TreeMap<String, Object>();
        for (var option : type.options()) {
            of(read(option, value), option.kind()).ifPresent(field -> converted.put(option.option().name(), field));
        }
        converting.remove(value);
        return Optional.of(Collections.unmodifiableMap(converted));
    }

    /** {@link TypeDrafts} made every option field accessible while reading the class's own defaults. */
    private static Object read(TypeDrafts.OptionDraft option, Object instance) {
        try {
            return option.option().field().get(instance);
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
