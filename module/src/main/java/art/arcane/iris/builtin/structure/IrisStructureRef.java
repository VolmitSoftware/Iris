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

package art.arcane.iris.builtin.structure;

import com.volmit.resource.ResourceId;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Iris extension: says that a text option names structures of the pack's jigsaw module by key, which Iris completes in
 * studio and carries along when it exports the pack. It stands for {@code @ResourceRef} of the structure kind, whose class
 * lives in the module jar and so cannot be named from this exposed package; Iris resolves it by the kind's id, {@link #KIND}.
 * To another host the option is plain text.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface IrisStructureRef {
    /** The id of the resource kind of the structures, which {@code iris:jigsaw} registers. */
    ResourceId KIND = ResourceId.parse("iris:jigsaw/structure");
}
