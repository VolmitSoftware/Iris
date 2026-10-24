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

package art.arcane.iris.engine.terrain;

import art.arcane.iris.core.modules.ModuleSettings;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.config.FeatureSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.host.ModuleLogs;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** A world of a pack's resources whose dimension declares the given settings and whose complex and terrain seeds are given, for tests of the terrain's support classes. */
public final class TestPackWorld implements PackWorld {
    private static final ResourceId DIMENSION = ResourceId.parse("test:dimension/overworld");

    private final PackResources resources;
    private final PackSettings settings;
    private final long seed;
    private final Map<Class<?>, Object> states = new HashMap<>();

    public TestPackWorld(PackResources resources, ModuleSettings dimensionSettings, long seed) {
        this(resources, new PackSettings() {
            @Override
            public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
                return dimensionStack(dimensionSettings, featureType);
            }
        }, seed);
    }

    /** A world whose pack declares the settings {@code settings} gives, wherever it says. */
    public TestPackWorld(PackResources resources, PackSettings settings, long seed) {
        this.resources = resources;
        this.settings = settings;
        this.seed = seed;
    }

    /** The settings the dimension declares, as a dimension in a column of a world holds them. */
    public static FeatureSettings dimensionLevel(ModuleSettings settings) {
        return new FeatureSettings() {
            @Override
            public <F extends IrisFeature> SettingsStack<F> at(Class<F> featureType, int worldX, int worldZ) {
                return dimensionStack(settings, featureType);
            }

            @Override
            public <F extends IrisFeature> Optional<F> declaredBy(ResourceId biome, Class<F> featureType) {
                return Optional.empty();
            }
        };
    }

    private static <F extends IrisFeature> SettingsStack<F> dimensionStack(ModuleSettings settings, Class<F> featureType) {
        return settings.find(featureType).map(value -> SettingsStack.of(List.of(new Entry<>(Level.DIMENSION, DIMENSION, value))))
                .orElseGet(SettingsStack::empty);
    }

    @Override
    public long seed() {
        return seed;
    }

    @Override
    public PackResources resources() {
        return resources;
    }

    @Override
    public PackSettings settings() {
        return settings;
    }

    @Override
    public int minWorldY() {
        return 0;
    }

    @Override
    public <T> T state(Class<T> type, Function<? super PackWorld, ? extends T> create) {
        return type.cast(states.computeIfAbsent(type, ignored -> create.apply(this)));
    }

    @Override
    public ModuleLog log() {
        return ModuleLogs.discarding();
    }

    @Override
    public long seed(ResourceId purpose) {
        return seed;
    }

    @Override
    public Optional<RunningWorld> running() {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return Optional.empty();
    }
}
