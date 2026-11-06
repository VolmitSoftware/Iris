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

import com.volmit.host.schema.ObjectType;
import com.volmit.host.schema.PackSchema;
import com.volmit.host.schema.SchemaOption;
import com.volmit.host.schema.ValueKind;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The settings types of the pack's schema a node graph shows, and every type they hold, as the studio reads them: each
 * setting with its kind, description, limits and default, so the studio builds and checks its forms from them.
 */
final class SchemaTypes {
    private SchemaTypes() {
    }

    /** {@code roots} and the types inside them, by name; a name the schema does not know is left out. */
    static Map<String, TypeWire> reachableFrom(PackSchema schema, Collection<String> roots) {
        Map<String, TypeWire> described = new LinkedHashMap<>();
        Deque<String> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            String name = pending.pop();
            if (described.containsKey(name)) {
                continue;
            }
            ObjectType type;
            try {
                type = schema.type(name);
            } catch (NoSuchElementException unknown) {
                continue;
            }
            described.put(name, new TypeWire(type.description(), type.options().stream().map(option -> optionOf(option, pending)).toList()));
        }
        return described;
    }

    private static OptionWire optionOf(SchemaOption option, Deque<String> pending) {
        return new OptionWire(option.name(), option.description(), option.required(),
                option.minimum().isPresent() ? option.minimum().getAsDouble() : null,
                option.maximum().isPresent() ? option.maximum().getAsDouble() : null,
                option.minItems().isPresent() ? option.minItems().getAsInt() : null,
                option.defaultValue().orElse(null),
                kindOf(option.kind(), pending));
    }

    @SuppressWarnings("removal")
    private static Map<String, Object> kindOf(ValueKind kind, Deque<String> pending) {
        Map<String, Object> wire = new LinkedHashMap<>();
        switch (kind) {
            case ValueKind.BooleanKind ignored -> wire.put("kind", "boolean");
            case ValueKind.IntegerKind ignored -> wire.put("kind", "integer");
            case ValueKind.DecimalKind ignored -> wire.put("kind", "decimal");
            case ValueKind.TextKind text -> {
                wire.put("kind", "text");
                wire.put("format", text.format().name());
            }
            case ValueKind.ReferenceKind reference -> {
                wire.put("kind", "reference");
                wire.put("reference", reference.reference().name());
                wire.put("format", reference.format().name());
            }
            case ValueKind.ResourceReferenceKind resource -> {
                wire.put("kind", "resource");
                wire.put("resource", resource.kind().toString());
            }
            case ValueKind.EnumKind enumKind -> {
                wire.put("kind", "enum");
                wire.put("constants", enumKind.constants().stream().map(constant -> Map.of("name", constant.name(), "description", constant.description())).toList());
            }
            case ValueKind.ListKind list -> {
                wire.put("kind", "list");
                wire.put("element", kindOf(list.element(), pending));
                wire.put("unique", list.unique());
            }
            case ValueKind.MapKind map -> {
                wire.put("kind", "map");
                wire.put("value", kindOf(map.value(), pending));
            }
            case ValueKind.ObjectKind object -> {
                pending.add(object.type());
                wire.put("kind", "object");
                wire.put("type", object.type());
            }
            case ValueKind.HostKind host -> {
                wire.put("kind", "host");
                wire.put("type", host.type());
            }
            case ValueKind.AnyKind ignored -> wire.put("kind", "any");
        }
        return wire;
    }

    /** A settings type: what it is for, and the settings a file of it may write. */
    record TypeWire(String description, List<OptionWire> options) {
    }

    /** One setting; a limit or default the schema does not give is null. */
    record OptionWire(String name, String description, boolean required, Double minimum, Double maximum, Integer minItems,
                      Object defaultValue, Map<String, Object> kind) {
    }
}
