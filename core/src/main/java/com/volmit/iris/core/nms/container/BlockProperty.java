package com.volmit.iris.core.nms.container;

import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class BlockProperty {
    private static final Set<Class<?>> NATIVES = Set.of(Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, Boolean.class, String.class);
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
        jsonFunction = NATIVES.contains(type) ? Function.identity() : this.nameFunction::apply;
    }

    public static <T extends Enum<T>> BlockProperty ofEnum(Class<T> type, String name, T defaultValue) {
        return new BlockProperty(
                name,
                type,
                defaultValue,
                Arrays.asList(type.getEnumConstants()),
                val -> val == null ? "null" : val.name()
        );
    }

    public static BlockProperty ofFloat(String name, float defaultValue, float min, float max, boolean exclusiveMin, boolean exclusiveMax) {
        return new BoundedDouble(
                name,
                defaultValue,
                min,
                max,
                exclusiveMin,
                exclusiveMax,
                (f) -> String.format("%.2f", f)
        );
    }

    public static BlockProperty ofBoolean(String name, boolean defaultValue) {
        return new BlockProperty(
                name,
                Boolean.class,
                defaultValue,
                List.of(true, false),
                (b) -> b ? "true" : "false"
        );
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

    public JSONObject buildJson() {
        var json = new JSONObject();
        json.put("type", jsonType());
        json.put("default", defaultValueAsJson());
        if (!values.isEmpty()) json.put("enum", valuesAsJson());
        return json;
    }

    public String jsonType() {
        if (type == Boolean.class)
            return "boolean";
        if (type == Byte.class || type == Short.class || type == Integer.class || type == Long.class)
            return "integer";
        if (type == Float.class || type == Double.class)
            return "number";
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

    private static class BoundedDouble extends BlockProperty {
        private final double min, max;
        private final boolean exclusiveMin, exclusiveMax;

        public BoundedDouble(
                String name,
                double defaultValue,
                double min,
                double max,
                boolean exclusiveMin,
                boolean exclusiveMax,
                Function<Double, String> nameFunction
        ) {
            super(name, Double.class, defaultValue, List.of(), nameFunction);
            this.min = min;
            this.max = max;
            this.exclusiveMin = exclusiveMin;
            this.exclusiveMax = exclusiveMax;
        }

        @Override
        public JSONObject buildJson() {
            return super.buildJson()
                    .put("minimum", min)
                    .put("maximum", max)
                    .put("exclusiveMinimum", exclusiveMin)
                    .put("exclusiveMaximum", exclusiveMax);
        }
    }
}
