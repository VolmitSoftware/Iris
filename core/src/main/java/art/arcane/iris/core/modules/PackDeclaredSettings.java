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
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.resource.PackResources;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
    public PackResources resources() {
        return data.getPackResources();
    }

    /** The pack's folder is offered as a {@link File}, for checks that read the files of the pack as written. */
    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return HostExtensions.as(type, type == File.class ? data.getDataFolder() : null);
    }

    @Override
    public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
        List<Entry<F>> entries = new ArrayList<>();
        addEntries(entries, featureType, Level.DIMENSION, IrisPackSettings.DIMENSION, data.getDimensionLoader());
        addEntries(entries, featureType, Level.REGION, IrisPackSettings.REGION, data.getRegionLoader());
        addEntries(entries, featureType, Level.BIOME, IrisPackSettings.BIOME, data.getBiomeLoader());
        return SettingsStack.of(entries);
    }

    private <F extends IrisFeature, R extends IrisRegistrant> void addEntries(List<Entry<F>> entries, Class<F> featureType,
                                                                             Level level, String kind, ResourceLoader<R> loader) {
        for (String key : Arrays.stream(loader.getPossibleKeys()).sorted().toList()) {
            R owner = loader.load(key, false);
            if (owner != null) {
                IrisPackSettings.addEntry(entries, featureType, level, ids.of(kind, key), owner);
            }
        }
    }
}
