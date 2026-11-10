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

package art.arcane.iris.modded.structure;

import com.volmit.tooling.StructureAccessException;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Reads the few private fields of vanilla classes that the tools need. Minecraft ships with its own names, so the field
 * names are the same on every loader; a field that is missing is a reading the platform cannot give, not a bug to hide.
 */
public final class PrivateFields {
    private PrivateFields() {
    }

    public static <T> T read(Object owner, String name, Class<T> type) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new StructureAccessException("Cannot read " + name + " of " + owner.getClass().getSimpleName() + ": " + failure, true, failure);
        }
    }

    public static List<?> readList(Object owner, String name) {
        return read(owner, name, List.class);
    }

    public static int readInt(Object owner, String name) {
        return read(owner, name, Integer.class);
    }
}
