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

import com.volmit.IrisFeature;
import com.volmit.config.SettingsStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The settings stacks of one pack snapshot, kept per settings type and per the module settings of the dimension, region
 * and surface biome that make each one, so that a column whose combination was seen before gets its stack without
 * allocating. Every registrant of a snapshot decodes its own module settings, so the instances, compared by identity,
 * stand for the registrants; a missing level is {@link ModuleSettings#NONE}, which declares nothing either.
 */
final class SettingsStacks {
    private final Map<ModuleSettings, Map<ModuleSettings, Map<ModuleSettings, Map<Class<?>, SettingsStack<?>>>>> byDimension =
            new ConcurrentHashMap<>();

    /** The stack remembered for the combination, or null when none was yet. */
    @SuppressWarnings("unchecked")
    <F extends IrisFeature> SettingsStack<F> find(ModuleSettings dimension, ModuleSettings region, ModuleSettings biome, Class<F> type) {
        var byRegion = byDimension.get(dimension);
        if (byRegion == null) {
            return null;
        }
        var byBiome = byRegion.get(region);
        if (byBiome == null) {
            return null;
        }
        var byType = byBiome.get(biome);
        return byType == null ? null : (SettingsStack<F>) byType.get(type);
    }

    <F extends IrisFeature> void remember(ModuleSettings dimension, ModuleSettings region, ModuleSettings biome, Class<F> type,
                                          SettingsStack<F> stack) {
        byDimension.computeIfAbsent(dimension, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(region, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(biome, ignored -> new ConcurrentHashMap<>())
                .put(type, stack);
    }
}
