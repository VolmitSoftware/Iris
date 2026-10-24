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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.spi.IrisLogging;
import com.volmit.item.ItemAttribute;
import com.volmit.resource.ResourceId;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Applies the attribute modifiers a module decided on to Bukkit items and entities. */
public final class BukkitAttributes {
    private static final Set<String> WARNED_ATTRIBUTES = ConcurrentHashMap.newKeySet();
    private static final List<String> GENERIC_PREFIXES = List.of("generic_", "generic.");
    private static final int MAX_NAME_LENGTH = 32;
    private static final String DEFAULT_NAME = "modifier";

    private BukkitAttributes() {
    }

    public static void apply(ItemAttribute modifier, ItemMeta meta) {
        Attribute attribute = resolveAttribute(modifier);
        if (attribute != null) {
            meta.addAttributeModifier(attribute, create(modifier));
        }
    }

    public static void apply(ItemAttribute modifier, Attributable target) {
        Attribute attribute = resolveAttribute(modifier);
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance != null) {
            instance.addModifier(create(modifier));
        }
    }

    /** The attribute the key names; a vanilla key may still carry the {@code generic_} or {@code generic.} prefix older versions had. */
    private static Attribute resolveAttribute(ItemAttribute modifier) {
        ResourceId id = modifier.attribute();
        Attribute resolved = Registry.ATTRIBUTE.get(new NamespacedKey(id.namespace(), id.path()));
        if (resolved == null && id.namespace().equals(NamespacedKey.MINECRAFT)) {
            resolved = withoutGenericPrefix(id.path());
        }
        if (resolved == null && WARNED_ATTRIBUTES.add(id.toString())) {
            IrisLogging.warn("Unknown attribute '" + id + "' in attribute-modifier '" + modifier.name() + "'");
        }
        return resolved;
    }

    private static Attribute withoutGenericPrefix(String path) {
        for (String prefix : GENERIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(path.substring(prefix.length())));
            }
        }
        return null;
    }

    private static AttributeModifier create(ItemAttribute modifier) {
        NamespacedKey key = NamespacedKey.minecraft(uniqueKey(modifier.name()));
        return new AttributeModifier(key, modifier.amount(), operationOf(modifier.operation()), EquipmentSlotGroup.ANY);
    }

    private static AttributeModifier.Operation operationOf(ItemAttribute.Operation operation) {
        return switch (operation) {
            case ADD_VALUE -> AttributeModifier.Operation.ADD_NUMBER;
            case ADD_MULTIPLIED_BASE -> AttributeModifier.Operation.ADD_SCALAR;
            case ADD_MULTIPLIED_TOTAL -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
        };
    }

    private static String uniqueKey(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        if (normalized.isBlank()) {
            normalized = DEFAULT_NAME;
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            normalized = normalized.substring(0, MAX_NAME_LENGTH);
        }
        return normalized + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
