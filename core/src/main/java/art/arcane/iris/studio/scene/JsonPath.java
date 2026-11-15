/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Where a value sits in a JSON file: member names and element indices from the root, such as
 * {@code parts, 0, stem} for the stem of the first part, built as {@code ROOT.member("parts").element(0).member("stem")}.
 */
public record JsonPath(List<Step> steps) {
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
        return steps.get(steps.size() - 1);
    }

    /** Whether the value here is an element of an array. */
    public boolean isElement() {
        return !isRoot() && last() instanceof Index;
    }

    public boolean startsWith(JsonPath prefix) {
        return steps.size() >= prefix.steps.size() && steps.subList(0, prefix.steps.size()).equals(prefix.steps);
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
