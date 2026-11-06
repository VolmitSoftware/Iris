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

package art.arcane.iris.modules.placement;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** The settings of one object placement as the placement stages read them, its markers and collisions resolved in the pack. */
public final class PlacementSettings {
    private final ObjectPlacement config;
    private final PackResources resources;

    public PlacementSettings(ObjectPlacement config, PackResources resources) {
        this.config = config;
        this.resources = resources;
    }

    public boolean forcePlace() {
        return config.forcePlace();
    }

    public SlopeClip slopeCondition() {
        return config.slopeCondition();
    }

    public boolean rotateTowardsSlope() {
        return config.rotateTowardsSlope();
    }

    public ObjectRotation rotation() {
        return config.rotation();
    }

    public ObjectTranslate translate() {
        return config.translate();
    }

    public ObjectPlaceMode mode() {
        return config.mode();
    }

    public boolean fromBottom() {
        return config.fromBottom();
    }

    /** Whether the object places from the ground up instead of by height checks. */
    public boolean bottom() {
        return config.bottom();
    }

    public CarvingMode carvingSupport() {
        return config.carvingSupport();
    }

    public boolean underwater() {
        return config.underwater();
    }

    public boolean onwater() {
        return config.onwater();
    }

    public boolean requireSurfaceSupport() {
        return config.requireSurfaceSupport();
    }

    public int surfaceSupportBuffer() {
        return config.surfaceSupportBuffer();
    }

    public int surfaceSupportDepth() {
        return config.surfaceSupportDepth();
    }

    public ObjectLimit clamp() {
        return config.clamp();
    }

    /** Whether air is placed before the object. */
    public boolean bore() {
        return config.bore();
    }

    public int boreExtendMinY() {
        return config.boreExtendMinY();
    }

    public int boreExtendMaxY() {
        return config.boreExtendMaxY();
    }

    public List<ObjectReplace> edit() {
        return config.edit();
    }

    public boolean waterloggable() {
        return config.waterloggable();
    }

    public boolean meld() {
        return config.meld();
    }

    public List<MarkerRule> markers() {
        return config.markers().stream()
                .map(marker -> new MarkerRule(marker.marker(), marker.mark().stream().map(spec -> resources.blocks().resolve(spec)).toList(),
                        marker.exact(), marker.maximumMarkers()))
                .toList();
    }

    public Optional<StiltSettings> stilt() {
        return config.stiltSettings();
    }

    public Optional<VacuumSettings> vacuum() {
        return config.vacuumSettings();
    }

    /** The snow filter's depth, 0 for none. */
    public double snow() {
        return config.snow();
    }

    /** The pack objects the placement may overlap even when {@link #forbiddenCollisions()} names them. */
    public Set<ResourceId> allowedCollisions() {
        return objectIds(config.allowedCollisions());
    }

    /** The pack objects the placement may not overlap. */
    public Set<ResourceId> forbiddenCollisions() {
        return objectIds(config.forbiddenCollisions());
    }

    private Set<ResourceId> objectIds(List<String> keys) {
        return keys.stream().filter(Objects::nonNull).map(resources::idOf).collect(Collectors.toUnmodifiableSet());
    }
}
