package com.volmit.iris.core.nms.container;

import com.volmit.iris.util.json.JSONArray;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public final class BlockProperty {
    private final String name;
    private final Class<?> type;

    private final Object defaultValue;
    private final Set<Object> values;
    private final Function<Object, String> nameFunction;
    private final Function<Object, Object> jsonFunction;

    public  <T extends Comparable<T>> BlockProperty(
            String name,
            Class<T> type,
            T defaultValue,
            Collection<T> values,
            Function<T, String> nameFunction
    ) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.values = Collections.unmodifiableSet(new TreeSet<>(values));
        this.nameFunction = (Function<Object, String>) (Object) nameFunction;
        jsonFunction = type == Boolean.class || type == Integer.class ?
                Function.identity() :
                this.nameFunction::apply;
    }

    @Override
    public @NotNull String toString() {
        return name + "=" + nameFunction.apply(defaultValue) + " [" + String.join(",", names()) + "]";
    }

    public String name() {
        return name;
    }

    public String defaultValue() {
        return nameFunction.apply(defaultValue);
    }

    public List<String> names() {
        return values.stream().map(nameFunction).toList();
    }

    public Object defaultValueAsJson() {
        return jsonFunction.apply(defaultValue);
    }

    public JSONArray valuesAsJson() {
        return new JSONArray(values.stream().map(jsonFunction).toList());
    }

    public String jsonType() {
        if (type == Boolean.class)
            return "boolean";
        if (type == Integer.class)
            return "integer";
        return "string";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (BlockProperty) obj;
        return Objects.equals(this.name, that.name) &&
                Objects.equals(this.values, that.values) &&
                Objects.equals(this.type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, values, type);
    }
}
