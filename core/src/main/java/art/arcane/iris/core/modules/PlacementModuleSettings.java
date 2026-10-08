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

import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.ResourceId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Module settings during one object placement: the world's levels at the column, followed by the placement's
 * own entry. A placement without an engine sees no world settings.
 */
final class PlacementModuleSettings implements FeatureSettings {
    private static final FeatureSettings NO_WORLD = new FeatureSettings() {
        @Override
        public <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, int worldX, int worldZ) {
            return List.of();
        }
    };

    private final FeatureSettings world;
    private final ModuleSettings placement;
    private final ResourceId source;

    /** {@code source} names the placement, such as the object it places. */
    PlacementModuleSettings(FeatureSettings world, ModuleSettings placement, ResourceId source) {
        this.world = world;
        this.placement = placement;
        this.source = source;
    }

    static FeatureSettings noWorld() {
        return NO_WORLD;
    }

    @Override
    public <F extends IrisFeature> List<Entry<F>> at(Class<F> featureType, int worldX, int worldZ) {
        List<Entry<F>> worldEntries = world.at(featureType, worldX, worldZ);
        Optional<F> own = placement.find(featureType);
        if (own.isEmpty()) {
            return worldEntries;
        }
        List<Entry<F>> entries = new ArrayList<>(worldEntries);
        entries.add(new Entry<>(Target.OBJECT_PLACEMENT, source, own.get()));
        return List.copyOf(entries);
    }
}
