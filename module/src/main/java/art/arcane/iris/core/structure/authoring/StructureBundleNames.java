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

package art.arcane.iris.core.structure.authoring;

import java.util.Locale;

/**
 * The names the structure importers give the bundles they write, which Iris's datapack ingest predicts to know which
 * bundles an import will leave and which it must clean up.
 */
public final class StructureBundleNames {
    private static final String DEFAULT_NAMESPACE = "minecraft";
    private static final String BUNDLE_PREFIX = "iris:";

    private StructureBundleNames() {
    }

    /** The bundle a registered structure imports as, such as {@code iris:minecraft_village_plains_town_centers}. */
    public static String ofStructure(String key) {
        return BUNDLE_PREFIX + structureName(key);
    }

    /** The bundle a structure template imports as. */
    public static String ofTemplate(String key) {
        return BUNDLE_PREFIX + templateName(key);
    }

    /** The name an imported structure is filed under. */
    public static String structureName(String key) {
        return key.toLowerCase().replace('/', '_').replace(':', '_');
    }

    /** The name an imported structure template is filed under; templates of the platform's own namespace need no prefix. */
    public static String templateName(String key) {
        int colon = key.indexOf(':');
        String namespace = colon >= 0 ? key.substring(0, colon) : DEFAULT_NAMESPACE;
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        String safePath = path.toLowerCase().replaceAll("[^a-z0-9_/-]", "_");
        while (safePath.contains("//")) {
            safePath = safePath.replace("//", "/");
        }
        if (safePath.startsWith("/")) {
            safePath = safePath.substring(1);
        }
        return DEFAULT_NAMESPACE.equals(namespace) ? safePath : namespace + "/" + safePath;
    }
}
