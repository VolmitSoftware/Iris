package com.volmit.tooling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Where a value sits in a JSON file: member names and element indices from the root, such as
 * {@code parts, 0, stem} for the stem of the first part, built as {@code ROOT.member("parts").element(0).member("stem")}.
 */
public record JsonPath(List<JsonPath.Step> steps) {
    public static final JsonPath ROOT = new JsonPath(List.of());

    public JsonPath {
        steps = List.copyOf(steps);
    }

    /** One step down from a value: into a member of an object or an element of an array. */
    public sealed interface Step permits Key, Index {
    }

    /** The member named {@code name} of an object. */
    public record Key(String name) implements Step {
        public Key {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** The element at {@code position} of an array, from 0. */
    public record Index(int position) implements Step {
        public Index {
            if (position < 0) {
                throw new IllegalArgumentException("An element index counts from 0, not " + position);
            }
        }

        @Override
        public String toString() {
            return String.valueOf(position);
        }
    }

    public JsonPath member(String name) {
        return then(new Key(name));
    }

    public JsonPath element(int position) {
        return then(new Index(position));
    }

    /** This path with {@code suffix}'s steps after its own. */
    public JsonPath resolve(JsonPath suffix) {
        List<Step> longer = new ArrayList<>(steps);
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
    public Step last() {
        requireStep();
        return steps.getLast();
    }

    /** Whether the value here is an element of an array. */
    public boolean isElement() {
        return !isRoot() && last() instanceof Index;
    }

    public boolean startsWith(JsonPath prefix) {
        return steps.size() >= prefix.steps.size() && steps.subList(0, prefix.steps.size()).equals(prefix.steps);
    }

    public int size() {
        return steps.size();
    }

    private JsonPath then(Step step) {
        List<Step> longer = new ArrayList<>(steps);
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
        return steps.isEmpty() ? "the root" : steps.stream().map(Step::toString).collect(Collectors.joining("."));
    }
}
