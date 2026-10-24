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
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.iris.engine.object.prefab.IrisObjectRotation;
import art.arcane.iris.engine.object.prefab.IrisObjectTranslate;
import com.volmit.block.BlockState;
import com.volmit.resource.ResourceId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** An Iris object placement's settings as the placement stages read them. */
final class IrisPlacementSettings implements PlacementSettings {
    private final IrisObjectPlacement config;
    private final IrisData data;
    private final PlatformBlockStates states;
    private final ObjectRotation rotation;

    IrisPlacementSettings(IrisObjectPlacement config, IrisData data, PlatformBlockStates states) {
        this.config = config;
        this.data = data;
        this.states = states;
        IrisObjectRotation requested = config.getRotation();
        this.rotation = ObjectRotation.of(requested.isEnabled(), requested.getXAxis(), requested.getYAxis(), requested.getZAxis());
    }

    /** The Iris rotation the settings were read from, if {@code rotation} is the one they gave. */
    boolean gaveRotation(ObjectRotation candidate) {
        return candidate == rotation;
    }

    IrisObjectRotation irisRotation() {
        return config.getRotation();
    }

    @Override
    public boolean forcePlace() {
        return config.isForcePlace();
    }

    @Override
    public SlopeClip slopeCondition() {
        return config.getSlopeCondition();
    }

    @Override
    public boolean rotateTowardsSlope() {
        return config.isRotateTowardsSlope();
    }

    @Override
    public ObjectRotation rotation() {
        return rotation;
    }

    @Override
    public ObjectTranslate translate() {
        IrisObjectTranslate translate = config.getTranslate();
        return ObjectTranslate.of(translate.getX(), translate.getY(), translate.getYRandom(), translate.getZ());
    }

    @Override
    public ObjectPlaceMode mode() {
        return config.getMode();
    }

    @Override
    public boolean fromBottom() {
        return config.isFromBottom();
    }

    @Override
    public boolean bottom() {
        return config.isBottom();
    }

    @Override
    public CarvingMode carvingSupport() {
        return config.getCarvingSupport();
    }

    @Override
    public boolean underwater() {
        return config.isUnderwater();
    }

    @Override
    public boolean onwater() {
        return config.isOnwater();
    }

    @Override
    public boolean requireSurfaceSupport() {
        return config.isRequireSurfaceSupport();
    }

    @Override
    public int surfaceSupportBuffer() {
        return config.getSurfaceSupportBuffer();
    }

    @Override
    public int surfaceSupportDepth() {
        return config.getSurfaceSupportDepth();
    }

    @Override
    public ObjectLimit clamp() {
        return config.getClamp();
    }

    @Override
    public boolean bore() {
        return config.isBore();
    }

    @Override
    public int boreExtendMinY() {
        return config.getBoreExtendMinY();
    }

    @Override
    public int boreExtendMaxY() {
        return config.getBoreExtendMaxY();
    }

    @Override
    public List<ObjectReplace> edit() {
        return config.getEdit();
    }

    @Override
    public boolean waterloggable() {
        return config.isWaterloggable();
    }

    @Override
    public boolean meld() {
        return config.isMeld();
    }

    @Override
    public List<MarkerRule> markers() {
        return config.getMarkers().stream().<MarkerRule>map(marker -> new Marker(marker, data)).toList();
    }

    @Override
    public Optional<StiltSettings> stilt() {
        return Optional.ofNullable(config.getStiltSettings());
    }

    @Override
    public Optional<VacuumSettings> vacuum() {
        return Optional.ofNullable(config.getVacuumSettings());
    }

    @Override
    public double snow() {
        return config.getSnow();
    }

    @Override
    public Set<ResourceId> allowedCollisions() {
        return objectIds(config.getAllowedCollisions());
    }

    @Override
    public Set<ResourceId> forbiddenCollisions() {
        return objectIds(config.getForbiddenCollisions());
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
