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

package art.arcane.iris.world.entity;

import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.platform.reflect.KeyedType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class PotionEffectTypes {
    private static final Set<String> MISSING_WARNED = ConcurrentHashMap.newKeySet();

    private PotionEffectTypes() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String upper = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (upper.contains(":")) {
            upper = upper.substring(upper.indexOf(':') + 1);
        }
        String path = upper.toLowerCase(Locale.ROOT);
        return ContentGate.POTION_EFFECT_ALIASES.getOrDefault(path, path).toUpperCase(Locale.ROOT);
    }

    public static PotionEffectType resolve(String rawName) {
        if (rawName == null || rawName.trim().isEmpty()) {
            return null;
        }
        String wanted = normalize(rawName);
        for (PotionEffectType i : Registry.EFFECT) {
            NamespacedKey key = KeyedType.getKey(i);
            if (key == null) {
                continue;
            }
            String candidate = key.getKey().toUpperCase(Locale.ROOT).replace(' ', '_');
            if (candidate.equals(wanted)) {
                return i;
            }
        }
        return null;
    }

    public static String knownTypesList() {
        TreeSet<String> names = new TreeSet<>();
        for (PotionEffectType i : Registry.EFFECT) {
            NamespacedKey key = KeyedType.getKey(i);
            if (key != null) {
                names.add(key.getKey().toUpperCase(Locale.ROOT).replace(' ', '_'));
            }
        }
        return String.join(", ", names);
    }

    public static boolean shouldWarn(String rawName) {
        return MISSING_WARNED.add(normalize(rawName));
    }
}
