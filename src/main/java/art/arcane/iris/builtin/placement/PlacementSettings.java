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

package art.arcane.iris.builtin.placement;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;

import java.util.List;
import java.util.Optional;

/** The settings of one object placement, as the placement stages read them. */
public interface PlacementSettings {
    boolean forcePlace();

    SlopeClip slopeCondition();

    boolean rotateTowardsSlope();

    ObjectRotation rotation();

    ObjectTranslate translate();

    ObjectPlaceMode mode();

    boolean fromBottom();

    /** Whether the object places from the ground up instead of by height checks. */
    boolean bottom();

    CarvingMode carvingSupport();

    boolean underwater();

    boolean onwater();

    boolean requireSurfaceSupport();

    int surfaceSupportBuffer();

    int surfaceSupportDepth();

    ObjectLimit clamp();

    /** Whether air is placed before the object. */
    boolean bore();

    int boreExtendMinY();

    int boreExtendMaxY();

    List<ObjectReplace> edit();

    boolean waterloggable();

    boolean meld();

    List<MarkerRule> markers();

    Optional<StiltSettings> stilt();

    Optional<VacuumSettings> vacuum();

    /** The snow filter's depth, 0 for none. */
    double snow();

    List<String> allowedCollisions();

    List<String> forbiddenCollisions();
}
