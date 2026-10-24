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

import art.arcane.iris.modules.terrain.BiomeLayers;
import art.arcane.iris.modules.terrain.LayerBlocks;
import com.volmit.ModuleLog;
import com.volmit.config.PackSettings;
import com.volmit.host.ModuleLogs;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.PackWorld;
import com.volmit.world.RunningWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/** The layer blocks of a pack's resources outside a running world, for tests that build {@link BiomeLayers} themselves. */
public final class TestLayerBlocks {
    private TestLayerBlocks() {
    }

    public static Supplier<LayerBlocks> of(PackResources resources) {
        LayerBlocks blocks = LayerBlocks.of(new ResourcesOnly(resources));
        return () -> blocks;
    }

    private record ResourcesOnly(PackResources resources, Map<Class<?>, Object> states) implements PackWorld {
        ResourcesOnly(PackResources resources) {
            this(resources, new HashMap<>());
        }

        @Override
        public long seed() {
            return 0;
        }

        @Override
        public long seed(ResourceId purpose) {
            return 0;
        }

        @Override
        public Optional<RunningWorld> running() {
            return Optional.empty();
        }

        @Override
        public PackSettings settings() {
            throw new UnsupportedOperationException("Only the pack's resources here");
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
    }
}
