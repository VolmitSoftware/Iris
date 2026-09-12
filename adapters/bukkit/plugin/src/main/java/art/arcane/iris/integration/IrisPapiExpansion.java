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

package art.arcane.iris.integration;

import art.arcane.volmlib.util.bukkit.papi.PlaceholderKeyRegistry;
import art.arcane.volmlib.util.bukkit.papi.VolmitPlaceholderExpansion;

import java.util.Objects;
import java.util.logging.Logger;

public final class IrisPapiExpansion extends VolmitPlaceholderExpansion {
    public static final String IDENTIFIER = "iris";
    public static final String AUTHOR = "Volmit Software";
    public static final String VERSION = "2.0.0";
    public static final String REQUIRED_PLUGIN = "Iris";

    public IrisPapiExpansion(IrisPapiState state, Logger logger) {
        super(IDENTIFIER, AUTHOR, VERSION, REQUIRED_PLUGIN, registry(state), logger);
    }

    public static PlaceholderKeyRegistry registry(IrisPapiState state) {
        Objects.requireNonNull(state, "state");

        return PlaceholderKeyRegistry.builder()
                .key("available", state::available)
                .key("world.available", state::worldAvailable)
                .key("world.biome", state::biome)
                .key("world.biome-key", state::biomeKey)
                .key("world.region", state::region)
                .key("world.region-key", state::regionKey)
                .key("world.dimension", state::dimension)
                .key("pregen.available", state::pregenAvailable)
                .key("pregen.world", state::pregenWorld)
                .key("pregen.percent", state::pregenPercent)
                .key("pregen.eta", state::pregenEta)
                .key("pregen.eta-text", state::pregenEtaText)
                .key("pregen.chunks", state::pregenChunks)
                .key("pregen.total", state::pregenTotal)
                .key("pregen.chunks-per-second", state::pregenChunksPerSecond)
                .key("pregen.paused", state::pregenPaused)
                .build();
    }
}
