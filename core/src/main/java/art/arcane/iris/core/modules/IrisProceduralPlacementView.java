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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.carve.CaveAnchorMode;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.procedural.IrisProceduralPlacement;
import art.arcane.iris.engine.object.procedural.ResolvedProceduralPlacement;
import art.arcane.iris.engine.object.procedural.tree.ResolvedTree;
import art.arcane.volmlib.util.math.RNG;

import java.util.List;
import java.util.Optional;

/** A procedural object of a region or biome as {@code iris:objects} sees it. */
final class IrisProceduralPlacementView implements ProceduralPlacement {
    private final IrisProceduralPlacement placement;
    private final IrisData data;
    private final IrisObjectPlacement options;

    IrisProceduralPlacementView(IrisProceduralPlacement placement, IrisData data) {
        this.placement = placement;
        this.data = data;
        this.options = placement.asPlacement();
    }

    /** The options this object is scattered with, as Iris's own placement. */
    IrisObjectPlacement options() {
        return options;
    }

    @Override
    public String name() {
        return placement.getName();
    }

    @Override
    public double chance() {
        return placement.getChance();
    }

    @Override
    public int density() {
        return placement.getDensity();
    }

    @Override
    public boolean plausible() {
        return placement.isPlausible();
    }

    @Override
    public boolean tree() {
        return placement instanceof ResolvedTree;
    }

    @Override
    public ObjectPlaceMode mode() {
        return options.getMode();
    }

    @Override
    public CarvingMode carvingSupport() {
        return options.getCarvingSupport();
    }

    @Override
    public CaveAnchorMode caveAnchorMode() {
        return options.getCaveAnchorMode();
    }

    @Override
    public boolean underwater() {
        return options.isUnderwater();
    }

    @Override
    public ObjectRotation rotation() {
        return shape().rotation();
    }

    @Override
    public ObjectTranslate translate() {
        return shape().translate();
    }

    @Override
    public Optional<VacuumSettings> vacuumSettings() {
        return Optional.ofNullable(shape().vacuumSettings());
    }

    private ResolvedProceduralPlacement.Shape shape() {
        return ((ResolvedProceduralPlacement) placement).shape();
    }

    @Override
    public Optional<PlaceableObject> variant(RNG rng) {
        return Optional.ofNullable(placement.getVariantObject(data, rng));
    }

    @Override
    public List<PlaceableObject> variants() {
        return List.copyOf(placement.getVariantObjects(data));
    }
}
