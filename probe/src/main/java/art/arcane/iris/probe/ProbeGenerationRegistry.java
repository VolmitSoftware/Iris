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

package art.arcane.iris.probe;

import art.arcane.iris.spi.PlatformGenerationRegistry;

import java.util.List;
import java.util.Locale;

final class ProbeGenerationRegistry implements PlatformGenerationRegistry {
    static final ProbeGenerationRegistry INSTANCE = new ProbeGenerationRegistry();

    private ProbeGenerationRegistry() {
    }

    @Override
    public String runtimeIdentity() {
        return "iris-probe";
    }

    @Override
    public List<String> legacyCustomBiomeResourceKeys(String packName, String dimensionKey, String customBiomeId) {
        return List.of(dimensionKey.toLowerCase(Locale.ROOT) + ":" + customBiomeId.toLowerCase(Locale.ROOT));
    }

    @Override
    public String dimensionTypeResourceKey(String packName, String dimensionKey, String dimensionTypeKey) {
        return "iris:" + dimensionTypeKey.toLowerCase(Locale.ROOT);
    }

    @Override
    public Definition definition(String registryKey, String resourceKey) {
        return Definition.resourceIdentity(registryKey, resourceKey);
    }
}
