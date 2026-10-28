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

package art.arcane.iris.modules.objects;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.PlacementSource;
import art.arcane.iris.builtin.prepare.AxisRotationClamp;
import art.arcane.iris.builtin.prepare.ObjectRotation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The object placement settings a placement is made with: an object placement's own, or the options a procedural object
 * is scattered with, with the changes {@code iris:objects} makes for where it places them. One per world, so each
 * procedural object's options are made once.
 */
public final class EffectiveSettings {
    private static final double UPSIDE_DOWN_TURN_DEGREES = 180D;
    private static final double QUARTER_TURN_DEGREES = 90D;

    private final Map<ProceduralPlacement, ObjectPlacement> proceduralOptions = new ConcurrentHashMap<>();

    /** The settings {@code placement} places with, with {@code changes} made to a copy of them. */
    public ObjectPlacement of(PlacementSource placement, PlacementChanges changes) {
        ObjectPlacement base = placement instanceof ObjectPlacement settings
                ? settings
                : proceduralOptions.computeIfAbsent((ProceduralPlacement) placement, EffectiveSettings::optionsOf);
        return withChanges(base, changes);
    }

    /** {@code base} with {@code changes} made to a copy of it; {@code base} itself when there are none. */
    public static ObjectPlacement withChanges(ObjectPlacement base, PlacementChanges changes) {
        if (changes.isNone()) {
            return base;
        }
        ObjectPlacement.Builder changed = base.toBuilder();
        if (changes.place() != null) {
            changed.place(changes.place());
        }
        if (changes.mode() != null) {
            changed.mode(changes.mode());
        }
        if (changes.upsideDown() != null && changes.upsideDown()) {
            changed.rotation(upsideDownRotation());
        }
        if (changes.carvingSupport() != null) {
            changed.carvingSupport(changes.carvingSupport());
        }
        if (changes.requireSurfaceSupport() != null) {
            changed.requireSurfaceSupport(changes.requireSurfaceSupport());
        }
        if (changes.surfaceSupportBuffer() != null) {
            changed.surfaceSupportBuffer(changes.surfaceSupportBuffer());
        }
        if (changes.forcePlace() != null) {
            changed.forcePlace(changes.forcePlace());
        }
        return changed.build();
    }

    /** The options a procedural object is scattered with, as an object placement. */
    private static ObjectPlacement optionsOf(ProceduralPlacement procedural) {
        return ObjectPlacement.builder()
                .mode(procedural.mode())
                .rotation(procedural.rotation())
                .clamp(procedural.clamp())
                .carvingSupport(procedural.carvingSupport())
                .underwater(procedural.underwater())
                .translate(procedural.translate())
                .stiltSettings(procedural.stiltSettings().orElse(null))
                .vacuumSettings(procedural.vacuumSettings().orElse(null))
                .chance(procedural.chance())
                .density(procedural.density())
                .surfaceSupportBuffer(procedural.surfaceSupportBuffer())
                .build();
    }

    private static ObjectRotation upsideDownRotation() {
        return ObjectRotation.of(true,
                AxisRotationClamp.of(true, true, UPSIDE_DOWN_TURN_DEGREES, UPSIDE_DOWN_TURN_DEGREES, QUARTER_TURN_DEGREES),
                AxisRotationClamp.of(true, false, 0D, 0D, QUARTER_TURN_DEGREES),
                new AxisRotationClamp());
    }
}
