/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import com.volmit.config.Reference;
import com.volmit.resource.PlatformRegistries;
import com.volmit.resource.ResourceId;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * The registries of the platform Iris runs on, as validators read them: the same lists studio completes options from.
 * Each registry is read once, when a validator first asks for it.
 */
public final class IrisPlatformRegistryIds implements PlatformRegistries {
    private static final String DEFAULT_NAMESPACE = "minecraft:";

    private final IrisPlatform platform;
    private final Map<Reference, Set<ResourceId>> idsByRegistry = new EnumMap<>(Reference.class);

    private IrisPlatformRegistryIds(IrisPlatform platform) {
        this.platform = platform;
    }

    /** The bound platform's registries; empty when no platform is bound, such as in a build. */
    public static Optional<PlatformRegistries> ofBoundPlatform() {
        return IrisPlatforms.isBound() ? Optional.of(new IrisPlatformRegistryIds(IrisPlatforms.get())) : Optional.empty();
    }

    @Override
    public synchronized Set<ResourceId> ids(Reference registry) {
        return idsByRegistry.computeIfAbsent(registry, this::read);
    }

    private Set<ResourceId> read(Reference registry) {
        return switch (registry) {
            case BLOCK -> idsOf(platform.registries().blockTypeKeys());
            case ITEM -> idsOf(platform.registries().itemKeys());
            case ENTITY -> idsOf(platform.registries().entityKeys());
            case STRUCTURE -> idsOf(platform.registries().structureKeys());
            case STRUCTURE_SET -> idsOf(structureHook(PlatformStructureHooks::structureSetKeys));
            case TEMPLATE_POOL -> idsOf(structureHook(PlatformStructureHooks::templatePoolKeys));
            case POTION_EFFECT -> idsOf(platform.registries().potionEffectKeys());
            case ENCHANTMENT -> idsOf(platform.registries().enchantmentKeys());
            case LOOT_TABLE -> idsOf(platform.registries().lootTableKeys());
            case STRUCTURE_PREFIX, SPECIAL_ENTITY, NOISE_STYLE -> Set.of();
        };
    }

    private List<String> structureHook(Function<PlatformStructureHooks, List<String>> keys) {
        PlatformStructureHooks hooks = platform.structureHooks();
        return hooks == null ? List.of() : keys.apply(hooks);
    }

    private static Set<ResourceId> idsOf(List<String> keys) {
        if (keys == null) {
            return Set.of();
        }
        Set<ResourceId> ids = new LinkedHashSet<>();
        for (String key : keys) {
            parsedId(key).ifPresent(ids::add);
        }
        return Set.copyOf(ids);
    }

    /** {@code key} as an id, {@code minecraft:} when it names no namespace; empty for a key that is no valid id. */
    private static Optional<ResourceId> parsedId(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        try {
            return Optional.of(ResourceId.parse(normalized.indexOf(':') >= 0 ? normalized : DEFAULT_NAMESPACE + normalized));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
