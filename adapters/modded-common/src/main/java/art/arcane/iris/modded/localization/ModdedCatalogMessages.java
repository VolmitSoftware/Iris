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

package art.arcane.iris.modded.localization;

import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.List;

public final class ModdedCatalogMessages {
    public static final TextKey MISSING = TextKey.of(
            "iris.modded.catalog.missing",
            "No worldgen catalog has been captured yet. Run /iris catalog refresh."
    );
    public static final TextKey HEADER = TextKey.of(
            "iris.modded.catalog.header",
            "Worldgen catalog ({name} {version}): {count} namespaces, {value} biomes, {value2} placed features, "
                    + "{value3} structures, {value4} dimension types, {value5} level stems"
    );
    public static final TextKey NAMESPACE = TextKey.of(
            "iris.modded.catalog.namespace",
            "{name} {version}: {value} biomes, {value2} placed features ({detail}), {value3} structures, "
                    + "{value4} dimension types, {value5} level stems"
    );
    public static final TextKey QUIET = TextKey.of(
            "iris.modded.catalog.quiet",
            "{count} more loaded mods have no worldgen content."
    );
    public static final TextKey UNKNOWN_NAMESPACE = TextKey.of(
            "iris.modded.catalog.unknown_namespace",
            "The worldgen catalog has no content from namespace {name}."
    );
    public static final TextKey DETAIL_BIOMES = TextKey.of(
            "iris.modded.catalog.detail_biomes",
            "Biomes ({count}): {value}"
    );
    public static final TextKey DETAIL_FEATURES = TextKey.of(
            "iris.modded.catalog.detail_features",
            "Placed features by type ({count}): {value}"
    );
    public static final TextKey DETAIL_STRUCTURES = TextKey.of(
            "iris.modded.catalog.detail_structures",
            "Structures ({count}): {value}"
    );
    public static final TextKey DETAIL_DIMENSIONS = TextKey.of(
            "iris.modded.catalog.detail_dimensions",
            "Dimension types: {value}; level stems: {value2}"
    );
    public static final TextKey REFRESHING = TextKey.of(
            "iris.modded.catalog.refreshing",
            "Capturing the worldgen catalog..."
    );
    public static final TextKey REFRESHED = TextKey.of(
            "iris.modded.catalog.refreshed",
            "Worldgen catalog captured in {duration}: {path}"
    );
    public static final TextKey REFRESH_FAILED = TextKey.of(
            "iris.modded.catalog.refresh_failed",
            "Worldgen catalog capture failed: {reason}"
    );
    public static final TextKey INGEST_INVALID = TextKey.of(
            "iris.modded.catalog.ingest_invalid",
            "Invalid arguments: {reason}. Usage: /iris ingest mods <source> <target> [dimension=<key>] "
                    + "[namespaces=<a,b>] [excludeRegions=<a,b>] [replace=true]"
    );
    public static final TextKey INGEST_PACK_MISSING = TextKey.of(
            "iris.modded.catalog.ingest_pack_missing",
            "Pack '{pack}' was not found under {path}."
    );
    public static final TextKey INGEST_STARTED = TextKey.of(
            "iris.modded.catalog.ingest_started",
            "Ingesting mod worldgen from '{source}' into '{target}' (dimension {dimension})..."
    );
    public static final TextKey INGEST_FAILED = TextKey.of(
            "iris.modded.catalog.ingest_failed",
            "Mod ingest failed: {reason}"
    );
    public static final TextKey INGEST_TARGET_EXISTS = TextKey.of(
            "iris.modded.catalog.ingest_target_exists",
            "Target pack '{target}' already exists. Add replace=true to overwrite it."
    );
    public static final TextKey INGEST_LINE = TextKey.of(
            "iris.modded.catalog.ingest_line",
            "  {summary}"
    );
    public static final TextKey INGEST_COMPLETE = TextKey.of(
            "iris.modded.catalog.ingest_complete",
            "Mod ingest finished: {path}"
    );
    public static final TextKey HELP_CATALOG = TextKey.of(
            "iris.modded.help.entry.command.catalog",
            "List the worldgen content of loaded mods, show one namespace, or capture it again"
    );
    public static final TextKey HELP_INGEST = TextKey.of(
            "iris.modded.help.entry.group.ingest",
            "Build Iris packs from the worldgen content of loaded mods"
    );
    public static final TextKey HELP_INGEST_MODS = TextKey.of(
            "iris.modded.help.entry.command.ingest_mods",
            "Copy a source pack into a new target pack and adopt the worldgen of loaded mods"
    );

    private static final List<MessageKey> KEYS = List.of(
            MISSING,
            HEADER,
            NAMESPACE,
            QUIET,
            UNKNOWN_NAMESPACE,
            DETAIL_BIOMES,
            DETAIL_FEATURES,
            DETAIL_STRUCTURES,
            DETAIL_DIMENSIONS,
            REFRESHING,
            REFRESHED,
            REFRESH_FAILED,
            INGEST_INVALID,
            INGEST_PACK_MISSING,
            INGEST_STARTED,
            INGEST_FAILED,
            INGEST_TARGET_EXISTS,
            INGEST_LINE,
            INGEST_COMPLETE,
            HELP_CATALOG,
            HELP_INGEST,
            HELP_INGEST_MODS
    );

    private ModdedCatalogMessages() {
    }

    public static List<MessageKey> keys() {
        return KEYS;
    }
}
