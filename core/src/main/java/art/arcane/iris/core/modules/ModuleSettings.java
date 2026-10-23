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
import com.volmit.config.LinkedBiomes;
import com.volmit.host.ConfiguredSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Module settings one dimension, region, biome or object placement declares, at most one value per settings
 * type. Each is one level's entry: levels stack and are never merged (see {@link ChunkModuleSettings}).
 */
public final class ModuleSettings {
    public static final ModuleSettings NONE = new ModuleSettings(Map.of());

    private final Map<Class<? extends IrisFeature>, IrisFeature> values;

    private ModuleSettings(Map<Class<? extends IrisFeature>, IrisFeature> values) {
        this.values = Map.copyOf(values);
    }

    static ModuleSettings of(Map<Class<? extends IrisFeature>, IrisFeature> values) {
        return values.isEmpty() ? NONE : new ModuleSettings(values);
    }

    public <S extends IrisFeature> Optional<S> find(Class<S> type) {
        return Optional.ofNullable(type.cast(values.get(type)));
    }

    public Collection<IrisFeature> values() {
        return values.values();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * The keys of the biomes these settings add to the biome tree of the owner's regions ({@link LinkedBiomes#treeBiomes}),
     * in a fixed order.
     */
    public Set<String> treeBiomes(IrisPackResources resources) {
        return linkedBiomes(resources, linked -> linked.treeBiomes(resources));
    }

    /**
     * The keys of the biomes these settings use besides ({@link LinkedBiomes#usedBiomes}), in a fixed order.
     *
     * @param dimension the settings of the dimension the owner is part of
     */
    public Set<String> usedBiomes(IrisPackResources resources, ModuleSettings dimension) {
        return linkedBiomes(resources, linked -> linked.usedBiomes(resources, dimension.values()));
    }

    private Set<String> linkedBiomes(IrisPackResources resources, Function<LinkedBiomes, Set<ResourceId>> pick) {
        if (values.isEmpty()) {
            return Set.of();
        }
        Set<String> biomes = new LinkedHashSet<>();
        values.values().stream()
                .filter(LinkedBiomes.class::isInstance)
                .sorted(Comparator.comparing(value -> value.getClass().getName()))
                .forEach(value -> pick.apply((LinkedBiomes) value).forEach(id -> biomes.add(resources.keyOf(PackResources.BIOMES, id))));
        return biomes;
    }

    /** These settings as one placement's own, declared by {@code source}. */
    public ConfiguredSettings placement(ResourceId source) {
        return isEmpty() ? ConfiguredSettings.none() : ConfiguredSettings.placement(source, values.values().toArray(IrisFeature[]::new));
    }
}
