package com.volmit.tooling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Where a value sits in a JSON file: member names and element indices from the root, such as
 * {@code parts, 0, stem} for the stem of the first part.
 *
 * @param steps each a {@link String} member name or a non-negative {@link Integer} element index
 */
public record JsonPath(List<Object> steps) {
    public static final JsonPath ROOT = new JsonPath(List.of());

    public JsonPath {
        steps = List.copyOf(steps);
        for (Object step : steps) {
            boolean index = step instanceof Integer at && at >= 0;
            if (!(step instanceof String) && !index) {
                throw new IllegalArgumentException("A JSON path step is a member name or an index from 0, not " + step);
            }
        }
    }

    public static JsonPath of(Object... steps) {
        return new JsonPath(List.of(steps));
    }

    public JsonPath member(String key) {
        Objects.requireNonNull(key, "key");
        return then(key);
    }

    public JsonPath element(int index) {
        return then(index);
    }

    /** This path with {@code suffix}'s steps after its own. */
    public JsonPath resolve(JsonPath suffix) {
        List<Object> longer = new ArrayList<>(steps);
        longer.addAll(suffix.steps);
        return new JsonPath(longer);
    }

    public boolean isRoot() {
        return steps.isEmpty();
    }

    /** @throws IllegalStateException at the root */
    public JsonPath parent() {
        requireStep();
        return new JsonPath(steps.subList(0, steps.size() - 1));
    }

    /** @throws IllegalStateException at the root */
    public Object last() {
        requireStep();
        return steps.getLast();
    }

    /** Whether the value here is an element of an array. */
    public boolean isElement() {
        return !isRoot() && last() instanceof Integer;
    }

    public boolean startsWith(JsonPath prefix) {
        return steps.size() >= prefix.steps.size() && steps.subList(0, prefix.steps.size()).equals(prefix.steps);
    }

    public int size() {
        return steps.size();
    }

    private JsonPath then(Object step) {
        List<Object> longer = new ArrayList<>(steps);
        longer.add(step);
        return new JsonPath(longer);
    }

    private void requireStep() {
        if (isRoot()) {
            throw new IllegalStateException("The root has no parent");
        }
    }

    @Override
    public String toString() {
        return steps.isEmpty() ? "the root" : steps.stream().map(String::valueOf).reduce((first, second) -> first + "." + second).orElseThrow();
    }
}
