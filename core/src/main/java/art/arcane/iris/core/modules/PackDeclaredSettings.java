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
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import com.volmit.FeatureAttachment.Target;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The settings a whole pack declares, for validators: every dimension of the pack, then every region, then every
 * biome, each in key order, whether a dimension reaches it or not. A file that fails to load declares nothing; its
 * loader reports it.
 */
final class PackDeclaredSettings implements PackSettings {
    private final IrisData data;
    private final PackResourceIds ids;

    PackDeclaredSettings(IrisData data) {
        this.data = data;
        this.ids = new PackResourceIds(data.getDataFolder());
    }

    @Override
    public <F extends IrisFeature> List<Entry<F>> declared(Class<F> featureType) {
        List<Entry<F>> entries = new ArrayList<>();
        addEntries(entries, featureType, Target.DIMENSION, IrisPackSettings.DIMENSION, data.getDimensionLoader());
        addEntries(entries, featureType, Target.REGION, IrisPackSettings.REGION, data.getRegionLoader());
        addEntries(entries, featureType, Target.BIOME, IrisPackSettings.BIOME, data.getBiomeLoader());
        return List.copyOf(entries);
    }

    private <F extends IrisFeature, R extends IrisRegistrant> void addEntries(List<Entry<F>> entries, Class<F> featureType,
                                                                             Target level, String kind, ResourceLoader<R> loader) {
        for (String key : Arrays.stream(loader.getPossibleKeys()).sorted().toList()) {
            R owner = loader.load(key, false);
            if (owner != null) {
                IrisPackSettings.addEntry(entries, featureType, level, ids.of(kind, key), owner);
            }
        }
    }
}
