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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import com.volmit.IrisFeature;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.resource.PackResources;

import java.util.List;

/**
 * The settings one dimension of a pack declares, with every region and biome it reaches, read before any world of it
 * exists: what the platform's structure setup and data pack installs see. The same entries {@link IrisPackSettings}
 * gives a world of the dimension.
 */
final class DimensionSettings implements PackSettings {
    private final IrisData data;
    private final IrisDimension dimension;
    private final PackResourceIds ids;

    DimensionSettings(IrisData data, IrisDimension dimension) {
        this.data = data;
        this.dimension = dimension;
        this.ids = new PackResourceIds(data.getDataFolder());
    }

    @Override
    public PackResources resources() {
        return data.getPackResources();
    }

    @Override
    public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
        return IrisPackSettings.declaredBy(dimension, () -> data, ids, featureType);
    }
}
