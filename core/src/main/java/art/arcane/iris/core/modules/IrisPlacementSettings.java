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
import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.builtin.carve.CarvingMode;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.placement.MarkerRule;
import art.arcane.iris.builtin.spawn.PlacementMarker;
import art.arcane.iris.builtin.placement.PlacementSettings;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.ObjectTranslate;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import art.arcane.iris.core.loader.IrisData;
import com.volmit.block.BlockState;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** An object placement's settings as the placement stages read them, its markers and collisions resolved in the pack. */
final class IrisPlacementSettings implements PlacementSettings {
    private final ObjectPlacement config;
    private final IrisData data;

    IrisPlacementSettings(ObjectPlacement config, IrisData data) {
        this.config = config;
        this.data = data;
    }

    @Override
    public boolean forcePlace() {
        return config.forcePlace();
    }

    @Override
    public SlopeClip slopeCondition() {
        return config.slopeCondition();
    }

    @Override
    public boolean rotateTowardsSlope() {
        return config.rotateTowardsSlope();
    }

    @Override
    public ObjectRotation rotation() {
        return config.rotation();
    }

    @Override
    public ObjectTranslate translate() {
        return config.translate();
    }

    @Override
    public ObjectPlaceMode mode() {
        return config.mode();
    }

    @Override
    public boolean fromBottom() {
        return config.fromBottom();
    }

    @Override
    public boolean bottom() {
        return config.bottom();
    }

    @Override
    public CarvingMode carvingSupport() {
        return config.carvingSupport();
    }

    @Override
    public boolean underwater() {
        return config.underwater();
    }

    @Override
    public boolean onwater() {
        return config.onwater();
    }

    @Override
    public boolean requireSurfaceSupport() {
        return config.requireSurfaceSupport();
    }

    @Override
    public int surfaceSupportBuffer() {
        return config.surfaceSupportBuffer();
    }

    @Override
    public int surfaceSupportDepth() {
        return config.surfaceSupportDepth();
    }

    @Override
    public ObjectLimit clamp() {
        return config.clamp();
    }

    @Override
    public boolean bore() {
        return config.bore();
    }

    @Override
    public int boreExtendMinY() {
        return config.boreExtendMinY();
    }

    @Override
    public int boreExtendMaxY() {
        return config.boreExtendMaxY();
    }

    @Override
    public List<ObjectReplace> edit() {
        return config.edit();
    }

    @Override
    public boolean waterloggable() {
        return config.waterloggable();
    }

    @Override
    public boolean meld() {
        return config.meld();
    }

    @Override
    public List<MarkerRule> markers() {
        return config.markers().stream().<MarkerRule>map(marker -> new Marker(marker, data)).toList();
    }

    @Override
    public Optional<StiltSettings> stilt() {
        return config.stiltSettings();
    }

    @Override
    public Optional<VacuumSettings> vacuum() {
        return config.vacuumSettings();
    }

    @Override
    public double snow() {
        return config.snow();
    }

    @Override
    public Set<ResourceId> allowedCollisions() {
        return objectIds(config.allowedCollisions());
    }

    @Override
    public Set<ResourceId> forbiddenCollisions() {
        return objectIds(config.forbiddenCollisions());
    }

    private Set<ResourceId> objectIds(List<String> keys) {
        return keys.stream().filter(Objects::nonNull).map(data.getPackResources()::idOf).collect(Collectors.toUnmodifiableSet());
    }

    private static final class Marker implements MarkerRule {
        private final PlacementMarker marker;
        private final IrisData data;

        private Marker(PlacementMarker marker, IrisData data) {
            this.marker = marker;
            this.data = data;
        }

        @Override
        public String marker() {
            return marker.marker();
        }

        @Override
        public List<BlockState> targets() {
            return marker.mark().stream().map(spec -> data.getPackResources().blocks().resolve(spec)).toList();
        }

        @Override
        public boolean exact() {
            return marker.exact();
        }

        @Override
        public int maximum() {
            return marker.maximumMarkers();
        }
    }
}
