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

package art.arcane.iris.builtin.objects;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import art.arcane.volmlib.util.math.RNG;

import java.util.List;
import java.util.Optional;

/**
 * A procedural object of a region or biome made placeable: a pool of variants Iris bakes once from its settings, and
 * the options it is scattered with, like an object placement.
 */
public interface ProceduralPlacement extends PlacementSource {
    String name();

    @Override
    double chance();

    int density();

    /** Whether the variants are meant to look like the real thing, so leaves decay. */
    boolean plausible();

    /** Whether the variants are trees. */
    boolean tree();

    @Override
    ObjectPlaceMode mode();

    @Override
    CarvingMode carvingSupport();

    @Override
    CaveAnchorMode caveAnchorMode();

    @Override
    boolean underwater();

    @Override
    default boolean requireSurfaceSupport() {
        return true;
    }

    @Override
    default int surfaceSupportBuffer() {
        return 2;
    }

    @Override
    default boolean isDolphinTarget() {
        return false;
    }

    ObjectRotation rotation();

    ObjectTranslate translate();

    Optional<VacuumSettings> vacuumSettings();

    /** The heights the variants may sit between. */
    ObjectLimit clamp();

    Optional<StiltSettings> stiltSettings();

    /** One of the variants, picked with {@code rng}; empty when none baked. */
    Optional<PlaceableObject> variant(RNG rng);

    /** Every variant, baked on first use. */
    List<PlaceableObject> variants();
}
