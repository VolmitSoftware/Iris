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

import com.volmit.host.DiscoveredModule;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

/**
 * Notices a pack that loads without the built-in modules jar. Such a pack still loads and generates, but quietly without
 * what the jar's modules do, so the owner is told once, with the fix.
 */
final class MissingBuiltInModules {
    static final String JAR_NAME = "iris-builtins.jar";

    private MissingBuiltInModules() {
    }

    /** What to tell the owner of {@code packFolder} whose pack loaded {@code packModules}; empty when the built-ins jar is there. */
    static Optional<String> warning(File packFolder, Collection<DiscoveredModule> packModules) {
        boolean builtInsLoaded = packModules.stream()
                .map(module -> module.info().id().toString())
                .anyMatch(IrisModuleHost.JAR_MODULE_IDS::contains);
        if (builtInsLoaded) {
            return Optional.empty();
        }
        return Optional.of("Pack " + packFolder.getName() + " loads without the built-in modules (" + JAR_NAME + "): its world gets"
                + " only its layout (biomes and heights), with no terrain blocks, ores, caves, decorations, objects or vanilla features,"
                + " and no loot, block drops, entity spawns or ambient effects. Copy " + JAR_NAME
                + " into " + new File(packFolder, PackModuleSet.MODULES_FOLDER).getPath() + " to fix it.");
    }
}
