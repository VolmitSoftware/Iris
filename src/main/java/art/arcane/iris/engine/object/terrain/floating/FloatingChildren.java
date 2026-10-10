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

package art.arcane.iris.engine.object.terrain.floating;

import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.builtin.floating.FloatingChildSettings;
import art.arcane.iris.builtin.floating.MergeFloatingChildrenSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.IrisBiome;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The {@link ResolvedFloatingChild} of every {@link FloatingChildBiome} an engine runtime has met, one per decoded entry. */
public final class FloatingChildren {
    private final Map<FloatingChildBiome, ResolvedFloatingChild> resolved = new ConcurrentHashMap<>();

    public FloatingChildren(Engine engine) {
    }

    FloatingChildren() {
    }

    /** The children of the engine {@code data} serves, or of {@code data} alone when no engine runs it. */
    public static FloatingChildren of(IrisData data) {
        Engine engine = data.getEngine();
        return engine == null ? new FloatingChildren() : of(engine);
    }

    public static FloatingChildren of(Engine engine) {
        return engine.getGeneration().state(FloatingChildren.class, FloatingChildren::new);
    }

    /** The floating children {@code biome} declares, in the order it lists them; none when it declares none. */
    public static List<FloatingChildBiome> declaredBy(IrisBiome biome) {
        return biome.getModuleSettings().find(FloatingChildSettings.class).map(FloatingChildSettings::children).orElse(List.of());
    }

    /** Whether {@code biome} samples every floating child on its own and unions their islands instead of picking one per column. */
    public static boolean merges(IrisBiome biome) {
        return biome.getModuleSettings().find(MergeFloatingChildrenSettings.class).map(MergeFloatingChildrenSettings::merge).orElse(false);
    }

    public ResolvedFloatingChild of(FloatingChildBiome settings) {
        return resolved.computeIfAbsent(settings, ResolvedFloatingChild::new);
    }

    public List<ResolvedFloatingChild> of(List<FloatingChildBiome> settings) {
        return settings.stream().map(this::of).toList();
    }
}
