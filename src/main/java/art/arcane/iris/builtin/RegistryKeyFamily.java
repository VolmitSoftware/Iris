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

package art.arcane.iris.builtin;

import java.util.Locale;

/**
 * How the settings of Iris's built-ins name families of the platform's registry keys, such as structures and placed
 * features: a pattern matches the keys equal to it and the keys it starts at a namespace, slash or underscore boundary,
 * ignoring case and surrounding space. So {@code minecraft:village} matches {@code minecraft:village_plains},
 * {@code minecraft:ore} every vanilla ore, and {@code nova_structures:} a whole namespace.
 */
public final class RegistryKeyFamily {
    private RegistryKeyFamily() {
    }

    /** Whether the family pattern {@code pattern} matches the registry key {@code key}. */
    public static boolean matches(String pattern, String key) {
        if (pattern == null || key == null) {
            return false;
        }
        String normalizedPattern = normalize(pattern);
        String normalizedKey = normalize(key);
        if (normalizedPattern.isEmpty() || !normalizedKey.startsWith(normalizedPattern)) {
            return false;
        }
        if (normalizedKey.length() == normalizedPattern.length()) {
            return true;
        }
        char patternEnd = normalizedPattern.charAt(normalizedPattern.length() - 1);
        if (patternEnd == ':' || patternEnd == '/' || patternEnd == '_') {
            return true;
        }
        char boundary = normalizedKey.charAt(normalizedPattern.length());
        return boundary == '/' || boundary == '_';
    }

    private static String normalize(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
