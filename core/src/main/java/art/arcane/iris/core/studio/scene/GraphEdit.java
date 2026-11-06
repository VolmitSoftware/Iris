/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.scene;

import art.arcane.iris.core.studio.scene.JsonSplicer.Splice;
import com.volmit.tooling.JsonPath;
import com.volmit.tooling.JsonText;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One change the studio makes to a file shown as nodes, as the text changes it makes there. Each rewrites only what it
 * changes, so the rest of the file keeps its author's layout.
 */
public sealed interface GraphEdit {
    /**
     * The splices that make the change in {@code file}.
     *
     * @throws IllegalArgumentException naming why, when the file has nothing there to change
     */
    List<Splice> splices(JsonSplicer file);

    /** Sets member {@code key} of the object at {@code object} to {@code literal}, writing the object when it is missing. */
    record Set(JsonPath object, String key, String literal) implements GraphEdit {
        public Set {
            Objects.requireNonNull(object, "object");
            Objects.requireNonNull(key, "key");
            if (!JsonText.isValue(literal)) {
                throw new IllegalArgumentException(literal + " is not a JSON value");
            }
        }

        @Override
        public List<Splice> splices(JsonSplicer file) {
            if (file.at(object).orElse(null) instanceof JsonText.Obj settings) {
                return List.of(file.put(settings, key, literal));
            }
            if (object.isRoot() || !(object.last() instanceof JsonPath.Key(String member))) {
                throw new IllegalArgumentException("Nothing to set " + key + " on at " + object);
            }
            return new Set(object.parent(), member, "{" + JsonSplicer.quote(key) + ": " + literal + "}").splices(file);
        }
    }

    /** Removes member {@code key} of the object at {@code object}, leaving it at its default. */
    record Unset(JsonPath object, String key) implements GraphEdit {
        @Override
        public List<Splice> splices(JsonSplicer file) {
            if (!(file.at(object).orElse(null) instanceof JsonText.Obj settings) || settings.member(key).isEmpty()) {
                return List.of();
            }
            return List.of(file.remove(settings, key));
        }
    }

    /** Appends {@code literal} to the array at {@code list}, writing the array when it is missing. */
    record Append(JsonPath list, String literal) implements GraphEdit {
        public Append {
            Objects.requireNonNull(list, "list");
            if (!JsonText.isValue(literal)) {
                throw new IllegalArgumentException(literal + " is not a JSON value");
            }
        }

        @Override
        public List<Splice> splices(JsonSplicer file) {
            return List.of(appendTo(file, list, literal));
        }
    }

    /** Appends a copy of the last element of the array at {@code list}, so a new item starts out filled in. */
    record AddItem(JsonPath list) implements GraphEdit {
        @Override
        public List<Splice> splices(JsonSplicer file) {
            if (!(file.at(list).orElse(null) instanceof JsonText.Arr array)) {
                throw new IllegalArgumentException("No list at " + list);
            }
            String copy = array.elements().isEmpty() ? "{}" : file.detachedTextOf(array.elements().getLast());
            return List.of(file.append(array, copy));
        }
    }

    /** Deletes the value at {@code path}, and the member holding the array it was in when it was the only element. */
    record Delete(JsonPath path) implements GraphEdit {
        public Delete {
            if (path.isRoot()) {
                throw new IllegalArgumentException("The file itself can't be deleted");
            }
        }

        @Override
        public List<Splice> splices(JsonSplicer file) {
            return List.of(removal(file, path));
        }
    }

    /** Appends a copy of the element at {@code path}, with all it holds, to the array holding it. */
    record Duplicate(JsonPath path) implements GraphEdit {
        public Duplicate {
            if (!path.isElement()) {
                throw new IllegalArgumentException("Only an element of a list can be duplicated, not " + path);
            }
        }

        @Override
        public List<Splice> splices(JsonSplicer file) {
            JsonText.Value element = file.at(path).orElseThrow(() -> new IllegalArgumentException("Nothing at " + path));
            if (!(file.at(path.parent()).orElse(null) instanceof JsonText.Arr array)) {
                throw new IllegalArgumentException("No list holds " + path);
            }
            return List.of(file.append(array, file.detachedTextOf(element)));
        }
    }

    /** Moves the element at {@code path}, with all it holds, to the end of the array at {@code toList}. */
    record Move(JsonPath path, JsonPath toList) implements GraphEdit {
        public Move {
            if (!path.isElement()) {
                throw new IllegalArgumentException("Only an element of a list can move, not " + path);
            }
            if (toList.startsWith(path)) {
                throw new IllegalArgumentException("A node can't move into itself or into what it holds");
            }
            if (toList.equals(path.parent())) {
                throw new IllegalArgumentException("It is there already");
            }
        }

        @Override
        public List<Splice> splices(JsonSplicer file) {
            String moved = file.detachedTextOf(file.at(path).orElseThrow(() -> new IllegalArgumentException("Nothing at " + path)));
            List<Splice> splices = new ArrayList<>();
            splices.add(removal(file, path));
            splices.add(appendTo(file, toList, moved));
            return splices;
        }
    }

    private static Splice appendTo(JsonSplicer file, JsonPath list, String literal) {
        if (file.at(list).orElse(null) instanceof JsonText.Arr array) {
            return file.append(array, literal);
        }
        if (list.isRoot() || !(list.last() instanceof JsonPath.Key(String member))
                || !(file.at(list.parent()).orElse(null) instanceof JsonText.Obj owner)) {
            throw new IllegalArgumentException("Nothing can be added at " + list);
        }
        return file.put(owner, member, "[" + literal + "]");
    }

    private static Splice removal(JsonSplicer file, JsonPath path) {
        JsonPath holderPath = path.parent();
        JsonText.Value holder = file.at(holderPath).orElseThrow(() -> new IllegalArgumentException("Nothing at " + path));
        if (path.last() instanceof JsonPath.Key(String key) && holder instanceof JsonText.Obj object) {
            return file.remove(object, key);
        }
        if (!(path.last() instanceof JsonPath.Index(int index)) || !(holder instanceof JsonText.Arr array) || index >= array.elements().size()) {
            throw new IllegalArgumentException("Nothing at " + path);
        }
        boolean onlyElement = array.elements().size() == 1;
        if (onlyElement && !holderPath.isRoot() && holderPath.last() instanceof JsonPath.Key(String key)
                && file.at(holderPath.parent()).orElse(null) instanceof JsonText.Obj owner) {
            return file.remove(owner, key);
        }
        return file.removeElement(array, index);
    }
}
