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

package art.arcane.iris.engine.loot;

import com.volmit.ModuleLog;
import com.volmit.item.ItemAttribute;
import com.volmit.item.DyeColor;
import com.volmit.resource.ContentKey;
import com.volmit.resource.ResourceId;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The platform things packs name, as they write them, turned into what IrisApi hands the host: ids, items and mobs,
 * attribute operations and colours. A blank key names nothing; a key that names nothing the host could look up is left
 * out with a warning, once per key.
 */
public final class PlatformKeys {
    private static final String PLATFORM_NAMESPACE = "minecraft";
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final String WARNING_KEY = "iris:platform-key/";

    private PlatformKeys() {
    }

    /** {@code key} as an id, such as {@code minecraft:sharpness} for {@code Sharpness}; see {@link ResourceId#ofPlatformKey}. */
    public static Optional<ResourceId> id(String key, ModuleLog log) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ResourceId.ofPlatformKey(key));
        } catch (IllegalArgumentException invalid) {
            log.warnOnce(WARNING_KEY + key, "Ignoring '" + key + "': it names nothing the platform could have");
            return Optional.empty();
        }
    }

    /**
     * The item {@code key} names. A key of the platform's own namespace, or of none, is a registered item; so is a key of
     * another namespace an id can hold as written, such as {@code itemsadder:ruby}, which the host routes to its owner.
     * Any other namespaced key is a provider's item, kept as written.
     */
    public static Optional<ContentKey> item(String key, ModuleLog log) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String written = key.trim();
        int separator = written.indexOf(NAMESPACE_SEPARATOR);
        if (separator < 0 || PLATFORM_NAMESPACE.equalsIgnoreCase(written.substring(0, separator))) {
            return id(written, log).map(ContentKey.Registered::new);
        }
        try {
            return Optional.of(new ContentKey.Registered(ResourceId.parse(written)));
        } catch (IllegalArgumentException notAnId) {
            return provided(written, log);
        }
    }

    /** A provider's mob or item, written {@code provider:name}, such as {@code mythicmobs:SkeletonKing}. */
    public static Optional<ContentKey> provided(String key, ModuleLog log) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String written = key.trim();
        int separator = written.indexOf(NAMESPACE_SEPARATOR);
        if (separator <= 0 || separator == written.length() - 1) {
            log.warnOnce(WARNING_KEY + key, "Ignoring '" + key + "': a provider's content is written provider:name");
            return Optional.empty();
        }
        return Optional.of(new ContentKey.Provided(written.substring(0, separator), written.substring(separator + 1)));
    }

    /**
     * The attribute operation packs name by Bukkit's names, {@code ADD_NUMBER}, {@code ADD_SCALAR} and
     * {@code MULTIPLY_SCALAR_1}, or by the platform's own; adding the amount for a name it does not know.
     */
    public static ItemAttribute.Operation operation(String name) {
        String normalized = name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ADD_SCALAR", "ADD_MULTIPLIED_BASE" -> ItemAttribute.Operation.ADD_MULTIPLIED_BASE;
            case "MULTIPLY_SCALAR_1", "ADD_MULTIPLIED_TOTAL" -> ItemAttribute.Operation.ADD_MULTIPLIED_TOTAL;
            default -> ItemAttribute.Operation.ADD_VALUE;
        };
    }

    /** The colour {@code text} writes, {@code #rrggbb}, {@code 0xrrggbb} or a number; empty, with a warning, for anything else. */
    public static OptionalInt rgb(String text, ModuleLog log) {
        if (text == null || text.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.decode(text.trim()) & 0xFFFFFF);
        } catch (NumberFormatException notAColour) {
            log.warnOnce(WARNING_KEY + text, "Ignoring the colour '" + text + "': write it as #rrggbb");
            return OptionalInt.empty();
        }
    }

    /** The dye colour {@code name} names, such as {@code RED} or {@code light_blue}; empty, with a warning, for anything else. */
    public static Optional<DyeColor> dye(String name, ModuleLog log) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(DyeColor.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            log.warnOnce(WARNING_KEY + name, "Ignoring the dye colour '" + name + "', which is none of the sixteen dyes");
            return Optional.empty();
        }
    }
}
