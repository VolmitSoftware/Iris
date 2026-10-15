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

package art.arcane.iris.modded.catalog;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModMetadata;

import java.util.List;
import java.util.Objects;

public record CatalogCaptureContext(String minecraftVersion, String platform, long capturedAtEpochMillis, List<NativeModMetadata> mods) {
    public CatalogCaptureContext {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(platform, "platform");
        mods = List.copyOf(Objects.requireNonNull(mods, "mods"));
    }
}
